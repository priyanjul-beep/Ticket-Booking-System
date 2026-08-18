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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DeadlockPreventionTest {

    @Autowired private BookingService bookingService;
    @Autowired private EventRepository eventRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BookingRepository bookingRepository;

    private Event event;
    private Seat seat1;
    private Seat seat2;
    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();

        event = eventRepository.save(Event.builder()
                .name("Deadlock Prevention Show")
                .venue("Hall B")
                .eventDate(java.time.ZonedDateTime.now().plusDays(7))
                .totalSeats(2)
                .availableSeats(2)
                .build());

        seat1 = seatRepository.save(Seat.builder()
                .event(event)
                .seatNumber("D-1")
                .category("VIP")
                .price(new BigDecimal("200.00"))
                .status(SeatStatus.AVAILABLE)
                .version(0L)
                .build());

        seat2 = seatRepository.save(Seat.builder()
                .event(event)
                .seatNumber("D-2")
                .category("VIP")
                .price(new BigDecimal("200.00"))
                .status(SeatStatus.AVAILABLE)
                .version(0L)
                .build());

        user1 = userRepository.save(User.builder().name("User A").email("usera_" + UUID.randomUUID() + "@test.com").build());
        user2 = userRepository.save(User.builder().name("User B").email("userb_" + UUID.randomUUID() + "@test.com").build());
    }

    @Test
    @DisplayName("User A [Seat 1, Seat 2] vs User B [Seat 2, Seat 1] -> Lock sorting prevents deadlock and completes in < 3s")
    void testDeadlockPrevention_ReversedSeatOrder() throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);

        // Thread 1: Requests [seat1, seat2]
        Future<?> future1 = executor.submit(() -> {
            try {
                startLatch.await();
                CreateBookingRequest req = CreateBookingRequest.builder()
                        .userId(user1.getId())
                        .eventId(event.getId())
                        .seatIds(List.of(seat1.getId(), seat2.getId())) // Order: 1, 2
                        .strategy(LockStrategyType.IN_MEMORY)
                        .idempotencyKey("dl-key-1")
                        .build();
                bookingService.createBooking(req);
                successCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        // Thread 2: Requests [seat2, seat1] in REVERSE ORDER
        Future<?> future2 = executor.submit(() -> {
            try {
                startLatch.await();
                CreateBookingRequest req = CreateBookingRequest.builder()
                        .userId(user2.getId())
                        .eventId(event.getId())
                        .seatIds(List.of(seat2.getId(), seat1.getId())) // Reverse Order: 2, 1
                        .strategy(LockStrategyType.IN_MEMORY)
                        .idempotencyKey("dl-key-2")
                        .build();
                bookingService.createBooking(req);
                successCount.incrementAndGet();
            } catch (Exception ignored) {}
        });

        startLatch.countDown();

        // Must complete within 3 seconds without deadlocking
        future1.get(3, TimeUnit.SECONDS);
        future2.get(3, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(successCount.get() >= 1, "At least one request must succeed without thread deadlock");
    }
}
