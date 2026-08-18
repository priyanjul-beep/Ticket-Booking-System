package com.ticketbooking.concurrency;

import com.ticketbooking.dto.CreateBookingRequest;
import com.ticketbooking.entity.Event;
import com.ticketbooking.entity.Seat;
import com.ticketbooking.entity.SeatStatus;
import com.ticketbooking.entity.User;
import com.ticketbooking.locking.LockStrategyType;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentBookingTest {

    @Autowired private BookingService bookingService;
    @Autowired private EventRepository eventRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BookingRepository bookingRepository;

    private Event event;
    private Seat seat;
    private List<User> users;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();

        event = eventRepository.save(Event.builder()
                .name("Single Seat Concurrency Show")
                .venue("Main Hall")
                .eventDate(java.time.ZonedDateTime.now().plusDays(7))
                .totalSeats(1)
                .availableSeats(1)
                .build());

        seat = seatRepository.save(Seat.builder()
                .event(event)
                .seatNumber("S-101")
                .category("PREMIUM")
                .price(new BigDecimal("150.00"))
                .status(SeatStatus.AVAILABLE)
                .version(0L)
                .build());

        users = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            users.add(userRepository.save(User.builder()
                    .name("User " + i)
                    .email("user" + i + "_" + UUID.randomUUID() + "@test.com")
                    .build()));
        }
    }

    @Test
    @DisplayName("100 Concurrent Threads attempt to book SAME SEAT -> Exactly 1 succeeds, 99 fail, 0 double bookings")
    void test100ThreadsSingleSeat_InMemoryLock() throws InterruptedException {
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to start simultaneously
                    CreateBookingRequest request = CreateBookingRequest.builder()
                            .userId(users.get(index).getId())
                            .eventId(event.getId())
                            .seatIds(List.of(seat.getId()))
                            .strategy(LockStrategyType.IN_MEMORY)
                            .idempotencyKey("idem-single-seat-" + index)
                            .build();

                    bookingService.createBooking(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        endLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly 1 user should successfully reserve the seat");
        assertEquals(99, failCount.get(), "Exactly 99 users should be rejected");
        assertEquals(1, bookingRepository.count(), "Database must contain exactly 1 booking (NO DOUBLE BOOKINGS)");

        Seat updatedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertEquals(SeatStatus.LOCKED, updatedSeat.getStatus(), "Seat status must be LOCKED");
    }
}
