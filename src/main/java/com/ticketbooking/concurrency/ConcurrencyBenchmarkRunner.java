package com.ticketbooking.concurrency;

import com.ticketbooking.dto.CreateBookingRequest;
import com.ticketbooking.entity.Event;
import com.ticketbooking.entity.Seat;
import com.ticketbooking.entity.SeatStatus;
import com.ticketbooking.entity.User;
import com.ticketbooking.locking.LockStrategyType;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.service.BookingService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency Benchmark Execution Engine.
 *
 * Runs automated comparative benchmarks across all locking strategies:
 * 1. NO_LOCK     (Unsafe Baseline)
 * 2. IN_MEMORY   (Java ReentrantLock)
 * 3. PESSIMISTIC (PostgreSQL SELECT FOR UPDATE)
 * 4. OPTIMISTIC  (JPA @Version)
 * 5. REDIS       (Redisson RLock)
 *
 * Collects latency distribution (Avg, P95, P99), throughput (ops/sec), success/failure counts,
 * and verifies zero double-bookings or overselling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConcurrencyBenchmarkRunner {

    private final BookingService bookingService;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkStrategyResult {
        private String strategy;
        private int totalRequests;
        private int totalAvailableSeats;
        private int successfulBookings;
        private int failedBookings;
        private double throughputOpsPerSec;
        private double avgLatencyMs;
        private double p95LatencyMs;
        private double p99LatencyMs;
        private boolean oversold;
        private int doubleBookedSeatsCount;
        private boolean worksAcrossMultipleJVMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkSuiteResult {
        private int concurrentUsers;
        private int totalSeats;
        private List<BenchmarkStrategyResult> strategyResults;
    }

    public BenchmarkSuiteResult runFullBenchmarkSuite(int concurrentUsers, int availableSeatsCount) throws InterruptedException {
        List<BenchmarkStrategyResult> results = new ArrayList<>();

        for (LockStrategyType strategy : LockStrategyType.values()) {
            try {
                BenchmarkStrategyResult res = benchmarkStrategy(strategy, concurrentUsers, availableSeatsCount);
                results.add(res);
            } catch (Exception e) {
                log.error("Failed benchmark for strategy {}", strategy, e);
            }
        }

        return BenchmarkSuiteResult.builder()
                .concurrentUsers(concurrentUsers)
                .totalSeats(availableSeatsCount)
                .strategyResults(results)
                .build();
    }

    public BenchmarkStrategyResult benchmarkStrategy(LockStrategyType strategy, int concurrentUsers, int seatsCount) throws InterruptedException {
        // Setup fresh benchmark event, seats, and users
        Event event = eventRepository.save(Event.builder()
                .name("Benchmark Event " + strategy + " " + UUID.randomUUID())
                .description("Load Test")
                .venue("Virtual Arena")
                .eventDate(java.time.ZonedDateTime.now().plusDays(7))
                .totalSeats(seatsCount)
                .availableSeats(seatsCount)
                .build());

        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= seatsCount; i++) {
            seats.add(Seat.builder()
                    .event(event)
                    .seatNumber("BM-" + i)
                    .category("VIP")
                    .price(new BigDecimal("100.00"))
                    .status(SeatStatus.AVAILABLE)
                    .version(0L)
                    .build());
        }
        List<Seat> savedSeats = seatRepository.saveAll(seats);

        List<User> users = new ArrayList<>();
        for (int i = 1; i <= Math.min(concurrentUsers, 100); i++) {
            users.add(userRepository.save(User.builder()
                    .name("User " + i)
                    .email("user" + i + "_" + UUID.randomUUID() + "@test.com")
                    .build()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(concurrentUsers, 50));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentUsers; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long reqStart = System.nanoTime();

                    User user = users.get(index % users.size());
                    Seat targetSeat = savedSeats.get(index % savedSeats.size());

                    CreateBookingRequest request = CreateBookingRequest.builder()
                            .userId(user.getId())
                            .eventId(event.getId())
                            .seatIds(List.of(targetSeat.getId()))
                            .strategy(strategy)
                            .idempotencyKey("bm-key-" + strategy + "-" + index + "-" + UUID.randomUUID())
                            .build();

                    bookingService.createBooking(request);
                    successCount.incrementAndGet();

                    long reqEnd = System.nanoTime();
                    latencies.add(TimeUnit.NANOSECONDS.toMillis(reqEnd - reqStart));

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        long totalDurationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // Calculate statistics
        List<Long> latencyList = new ArrayList<>(latencies);
        Collections.sort(latencyList);

        double avgLatency = latencyList.isEmpty() ? 0 : latencyList.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double p95 = latencyList.isEmpty() ? 0 : latencyList.get((int) (latencyList.size() * 0.95));
        double p99 = latencyList.isEmpty() ? 0 : latencyList.get((int) (latencyList.size() * 0.99));
        double throughput = totalDurationMs > 0 ? (concurrentUsers * 1000.0) / totalDurationMs : 0;

        boolean oversold = successCount.get() > seatsCount;
        boolean multiJvm = strategy == LockStrategyType.REDIS || strategy == LockStrategyType.PESSIMISTIC || strategy == LockStrategyType.OPTIMISTIC;

        return BenchmarkStrategyResult.builder()
                .strategy(strategy.name())
                .totalRequests(concurrentUsers)
                .totalAvailableSeats(seatsCount)
                .successfulBookings(successCount.get())
                .failedBookings(failCount.get())
                .throughputOpsPerSec(Math.round(throughput * 100.0) / 100.0)
                .avgLatencyMs(Math.round(avgLatency * 100.0) / 100.0)
                .p95LatencyMs(Math.round(p95 * 100.0) / 100.0)
                .p99LatencyMs(Math.round(p99 * 100.0) / 100.0)
                .oversold(oversold)
                .doubleBookedSeatsCount(oversold ? successCount.get() - seatsCount : 0)
                .worksAcrossMultipleJVMs(multiJvm)
                .build();
    }
}
