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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentMultiSeatBookingTest {

    @Autowired private BookingService bookingService;
    @Autowired private EventRepository eventRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BookingRepository bookingRepository;

    private Event event;
    private List<Seat> seats;
    private List<User> users;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();

        event = eventRepository.save(Event.builder()
                .name("Flash Concert 10 Seats")
                .venue("Arena")
                .eventDate(java.time.ZonedDateTime.now().plusDays(7))
                .totalSeats(10)
                .availableSeats(10)
                .build());

        seats = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            seats.add(seatRepository.save(Seat.builder()
                    .event(event)
                    .seatNumber("M-" + i)
                    .category("STANDARD")
                    .price(new BigDecimal("100.00"))
                    .status(SeatStatus.AVAILABLE)
                    .version(0L)
                    .build()));
        }

        users = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            users.add(userRepository.save(User.builder()
                    .name("User " + i)
                    .email("muser" + i + "_" + UUID.randomUUID() + "@test.com")
                    .build()));
        }
    }

    @Test
    @DisplayName("1000 Concurrent Requests for 10 Seats -> Exactly 10 successful bookings, 990 failures, 0 overselling")
    void test1000Requests10Seats_PessimisticLock() throws InterruptedException {
        int requests = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(requests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < requests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    User user = users.get(index % users.size());
                    Seat targetSeat = seats.get(index % seats.size());

                    CreateBookingRequest request = CreateBookingRequest.builder()
                            .userId(user.getId())
                            .eventId(event.getId())
                            .seatIds(List.of(targetSeat.getId()))
                            .strategy(LockStrategyType.PESSIMISTIC)
                            .idempotencyKey("idem-multi-pessimistic-" + index)
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

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertEquals(10, successCount.get(), "Exactly 10 seats should be successfully booked");
        assertEquals(990, failCount.get(), "Exactly 990 requests should be rejected");
        assertEquals(10, bookingRepository.count(), "Exactly 10 bookings should exist in DB");

        Event updatedEvent = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(0, updatedEvent.getAvailableSeats(), "Remaining available seats must be exactly 0");
    }
}
