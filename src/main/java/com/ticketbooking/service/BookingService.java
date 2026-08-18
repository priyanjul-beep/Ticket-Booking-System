package com.ticketbooking.service;

import com.ticketbooking.config.AppConfig;
import com.ticketbooking.dto.BookingDTO;
import com.ticketbooking.dto.CreateBookingRequest;
import com.ticketbooking.dto.SeatDTO;
import com.ticketbooking.entity.*;
import com.ticketbooking.exception.*;
import com.ticketbooking.locking.*;
import com.ticketbooking.metrics.BookingMetricsService;
import com.ticketbooking.payment.PaymentOutcome;
import com.ticketbooking.payment.PaymentService;
import com.ticketbooking.repository.*;
import com.ticketbooking.util.BookingReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Core Booking Engine supporting multiple Concurrency & Locking Strategies:
 *
 * 1. IN_MEMORY   — Java ReentrantLock (single-JVM)
 * 2. PESSIMISTIC — Database row lock (SELECT FOR UPDATE)
 * 3. OPTIMISTIC  — JPA @Version CAS check
 * 4. REDIS       — Redisson distributed lock across multiple JVM instances
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final LockStrategyFactory lockStrategyFactory;
    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;
    private final BookingMetricsService metricsService;
    private final AppConfig appConfig;

    /**
     * Create a booking with idempotency check and explicit locking strategy.
     */
    public BookingDTO createBooking(CreateBookingRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. IDEMPOTENCY CHECK
        Optional<IdempotencyRecord> existing = idempotencyService.getRecord(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("IDEMPOTENCY_HIT for key: {}", request.getIdempotencyKey());
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.findAndRegisterModules();
                return mapper.readValue(existing.get().getResponseBody(), BookingDTO.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached booking result", e);
            }
        }

        LockStrategyType strategyType = request.getStrategy() != null ? request.getStrategy() : LockStrategyType.REDIS;
        BookingDTO result;

        switch (strategyType) {
            case IN_MEMORY, REDIS -> result = createBookingWithDistributedOrInMemoryLock(request, strategyType);
            case PESSIMISTIC -> result = createBookingWithPessimisticLock(request);
            case OPTIMISTIC -> result = createBookingWithOptimisticLock(request);
            default -> throw new IllegalArgumentException("Unsupported strategy: " + strategyType);
        }

        metricsService.recordLatency(System.currentTimeMillis() - startTime);
        metricsService.recordBookingSuccess();

        // Save idempotency record
        idempotencyService.saveRecord(request.getIdempotencyKey(), result, 201);

        return result;
    }

    /**
     * Strategy 1 & 4: In-Memory (ReentrantLock) or Redis (Redisson RLock).
     *
     * FLOW:
     *   Acquire Lock -> Start DB Transaction -> Reserve Seats -> Create Pending Booking -> Commit -> Release Lock
     */
    private BookingDTO createBookingWithDistributedOrInMemoryLock(CreateBookingRequest request, LockStrategyType strategyType) {
        SeatLockStrategy lockStrategy = lockStrategyFactory.getStrategy(strategyType);

        List<String> lockKeys = request.getSeatIds().stream()
                .map(id -> "seat:" + id)
                .toList();

        List<String> acquiredLocks = Collections.emptyList();
        try {
            log.info("ACQUIRING_LOCKS strategy: {}, seats: {}", strategyType, lockKeys);
            acquiredLocks = lockStrategy.tryLockAll(
                    lockKeys,
                    appConfig.getLockWaitSeconds(),
                    appConfig.getLockLeaseSeconds(),
                    TimeUnit.SECONDS
            );

            if (acquiredLocks.isEmpty()) {
                metricsService.recordLockFailed();
                metricsService.recordBookingFailure();
                throw new LockAcquisitionException("Could not acquire lock for requested seats. Please try again.");
            }

            metricsService.recordLockAcquired();

            // Execute DB transaction while locks are held
            return executeBookingTransaction(request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metricsService.recordBookingFailure();
            throw new LockAcquisitionException("Interrupted while waiting for seat lock");
        } finally {
            if (!acquiredLocks.isEmpty()) {
                lockStrategy.unlockAll(acquiredLocks);
                log.info("RELEASED_LOCKS strategy: {}, seats: {}", strategyType, acquiredLocks);
            }
        }
    }

    /**
     * Strategy 2: Database Pessimistic Locking (SELECT ... FOR UPDATE).
     *
     * Lock acquisition is deferred directly to PostgreSQL row locks inside the transaction.
     */
    @Transactional
    public BookingDTO createBookingWithPessimisticLock(CreateBookingRequest request) {
        log.info("PESSIMISTIC_LOCK_BOOKING_START for seats: {}", request.getSeatIds());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.getEventId()));

        // SORT IDs to prevent deadlocks:
        // Request 1 [1, 2], Request 2 [2, 1] -> both execute SELECT FOR UPDATE in order 1, 2
        List<Long> sortedSeatIds = new ArrayList<>(request.getSeatIds());
        Collections.sort(sortedSeatIds);

        // Fetch rows WITH PESSIMISTIC WRITE LOCK (SELECT FOR UPDATE)
        List<Seat> seats = seatRepository.findAllByIdWithPessimisticLock(sortedSeatIds);

        if (seats.size() != sortedSeatIds.size()) {
            throw new ResourceNotFoundException("One or more seats not found");
        }

        for (Seat seat : seats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                metricsService.recordBookingFailure();
                throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " is no longer available");
            }
        }

        // Lock seats
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.LOCKED);
            totalAmount = totalAmount.add(seat.getPrice());
        }
        seatRepository.saveAll(seats);

        // Decrement event available seats safely
        int updated = eventRepository.decrementAvailableSeats(event.getId(), seats.size());
        if (updated == 0) {
            throw new SeatUnavailableException("Not enough available seats in event");
        }

        // Create booking
        Booking booking = Booking.builder()
                .bookingReference(BookingReferenceGenerator.generateBookingReference())
                .user(user)
                .event(event)
                .status(BookingStatus.PENDING)
                .totalAmount(totalAmount)
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(ZonedDateTime.now().plusMinutes(appConfig.getBookingExpiryMinutes()))
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        for (Seat seat : seats) {
            BookingSeat bs = BookingSeat.builder()
                    .booking(savedBooking)
                    .seat(seat)
                    .build();
            bookingSeatRepository.save(bs);
        }

        return mapToDTO(savedBooking, seats);
    }

    /**
     * Strategy 3: Optimistic Locking via JPA @Version.
     *
     * Does not block on read. Hibernate validates version column on UPDATE.
     * Throws OptimisticLockException if version has changed.
     */
    @Transactional
    public BookingDTO createBookingWithOptimisticLock(CreateBookingRequest request) {
        log.info("OPTIMISTIC_LOCK_BOOKING_START for seats: {}", request.getSeatIds());
        return executeBookingTransaction(request);
    }

    /**
     * Helper to execute seat reservation transaction.
     */
    @Transactional
    public BookingDTO executeBookingTransaction(CreateBookingRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.getEventId()));

        List<Seat> seats = seatRepository.findAllByIds(request.getSeatIds());
        if (seats.size() != request.getSeatIds().size()) {
            throw new ResourceNotFoundException("One or more seats not found");
        }

        for (Seat seat : seats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                metricsService.recordBookingFailure();
                throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " is already " + seat.getStatus());
            }
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.LOCKED);
            totalAmount = totalAmount.add(seat.getPrice());
        }
        seatRepository.saveAll(seats); // JPA @Version incremented here for Optimistic Locking

        int updated = eventRepository.decrementAvailableSeats(event.getId(), seats.size());
        if (updated == 0) {
            throw new SeatUnavailableException("Not enough available seats in event");
        }

        Booking booking = Booking.builder()
                .bookingReference(BookingReferenceGenerator.generateBookingReference())
                .user(user)
                .event(event)
                .status(BookingStatus.PENDING)
                .totalAmount(totalAmount)
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(ZonedDateTime.now().plusMinutes(appConfig.getBookingExpiryMinutes()))
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        for (Seat seat : seats) {
            BookingSeat bs = BookingSeat.builder()
                    .booking(savedBooking)
                    .seat(seat)
                    .build();
            bookingSeatRepository.save(bs);
        }

        return mapToDTO(savedBooking, seats);
    }

    /**
     * Process payment for a PENDING booking.
     */
    @Transactional
    public BookingDTO processBookingPayment(Long bookingId, PaymentOutcome forcedOutcome) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is not in PENDING status: " + booking.getStatus());
        }

        PaymentOutcome outcome = (forcedOutcome != null)
                ? paymentService.processPaymentWithOutcome(booking.getBookingReference(), booking.getTotalAmount(), forcedOutcome)
                : paymentService.processPayment(booking.getBookingReference(), booking.getTotalAmount());

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        List<Seat> seats = bookingSeats.stream().map(BookingSeat::getSeat).toList();

        if (outcome == PaymentOutcome.SUCCESS) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setConfirmedAt(ZonedDateTime.now());
            for (Seat seat : seats) {
                seat.setStatus(SeatStatus.BOOKED);
            }
            seatRepository.saveAll(seats);
            metricsService.recordPaymentSuccess();
            log.info("BOOKING_CONFIRMED for ref: {}", booking.getBookingReference());
        } else {
            booking.setStatus(BookingStatus.CANCELLED);
            for (Seat seat : seats) {
                seat.setStatus(SeatStatus.AVAILABLE);
            }
            seatRepository.saveAll(seats);
            eventRepository.incrementAvailableSeats(booking.getEvent().getId(), seats.size());
            metricsService.recordPaymentFailure();
            log.warn("BOOKING_CANCELLED (Payment Failure) for ref: {}, seats returned to available", booking.getBookingReference());
        }

        Booking updated = bookingRepository.save(booking);
        return mapToDTO(updated, seats);
    }

    @Transactional(readOnly = true)
    public BookingDTO getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));
        List<Seat> seats = bookingSeatRepository.findByBookingId(id).stream().map(BookingSeat::getSeat).toList();
        return mapToDTO(booking, seats);
    }

    public BookingDTO mapToDTO(Booking booking, List<Seat> seats) {
        List<SeatDTO> seatDTOs = seats.stream().map(s -> SeatDTO.builder()
                .id(s.getId())
                .eventId(s.getEvent().getId())
                .seatNumber(s.getSeatNumber())
                .category(s.getCategory())
                .price(s.getPrice())
                .status(s.getStatus())
                .version(s.getVersion())
                .build()).toList();

        return BookingDTO.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUser().getId())
                .eventId(booking.getEvent().getId())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .idempotencyKey(booking.getIdempotencyKey())
                .seats(seatDTOs)
                .createdAt(booking.getCreatedAt())
                .expiresAt(booking.getExpiresAt())
                .confirmedAt(booking.getConfirmedAt())
                .build();
    }
}
