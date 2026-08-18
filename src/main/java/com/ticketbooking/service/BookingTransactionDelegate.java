package com.ticketbooking.service;

import com.ticketbooking.config.AppConfig;
import com.ticketbooking.dto.BookingDTO;
import com.ticketbooking.dto.CreateBookingRequest;
import com.ticketbooking.dto.SeatDTO;
import com.ticketbooking.entity.*;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.exception.SeatUnavailableException;
import com.ticketbooking.metrics.BookingMetricsService;
import com.ticketbooking.repository.*;
import com.ticketbooking.util.BookingReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transactional Delegate for Booking Operations.
 *
 * WHY THIS CLASS IS NEEDED:
 * Spring's `@Transactional` uses AOP proxies. When a method inside `BookingService` calls another
 * method in the same class (self-invocation), the AOP proxy is bypassed and the transaction is NOT started.
 *
 * By isolating database persistence into this separate Spring bean, calls from `BookingService`
 * (e.g. while holding a Redis or Java ReentrantLock) go through Spring's proxy, guaranteeing
 * proper transaction boundaries, isolation levels, and rollback semantics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingTransactionDelegate {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingMetricsService metricsService;
    private final AppConfig appConfig;

    /**
     * Standard transactional reservation flow (used by In-Memory, Redis, and Optimistic locking).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        seatRepository.saveAll(seats); // Optimistic locking (@Version) triggered on update

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
     * Database Pessimistic Locking Flow (SELECT FOR UPDATE).
     *
     * Locks rows at PostgreSQL level inside the transaction.
     * Seat IDs are sorted lexicographically/numerically to prevent DB deadlocks.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDTO createBookingWithPessimisticLock(CreateBookingRequest request) {
        log.info("PESSIMISTIC_LOCK_BOOKING_START for seats: {}", request.getSeatIds());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.getEventId()));

        // SORT seat IDs deterministically to prevent deadlocks:
        // Request 1 [1, 2], Request 2 [2, 1] -> both execute SELECT FOR UPDATE in order 1, then 2
        List<Long> sortedSeatIds = new ArrayList<>(request.getSeatIds());
        Collections.sort(sortedSeatIds);

        // Fetch rows WITH PESSIMISTIC WRITE LOCK (SELECT ... FOR UPDATE)
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

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.LOCKED);
            totalAmount = totalAmount.add(seat.getPrice());
        }
        seatRepository.saveAll(seats);

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

    private BookingDTO mapToDTO(Booking booking, List<Seat> seats) {
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
