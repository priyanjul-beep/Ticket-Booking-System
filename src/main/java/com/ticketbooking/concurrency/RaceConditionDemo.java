package com.ticketbooking.concurrency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interactive Race Condition Demonstration Module.
 *
 * DEMONSTRATES:
 * 1. Unsafe Read-Modify-Write (`available_seats = count - 1`)
 *    - 1,000 threads read initial count (e.g. 100).
 *    - Multiple threads read 100 before any write occurs.
 *    - Final inventory is corrupted (e.g., lost updates cause incorrect seat count).
 *
 * 2. Safe Atomic SQL Update (`available_seats = available_seats - 1 WHERE available_seats > 0`)
 *    - 1,000 threads execute atomic SQL decrement.
 *    - Exactly 10 updates succeed; remaining 990 return 0 rows updated.
 *    - Final inventory is exactly 0 (no overselling, no corrupt count).
 */
@Slf4j
@Component
public class RaceConditionDemo {

    private final JdbcTemplate jdbcTemplate;

    public RaceConditionDemo(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DemoResult {
        private String mode;
        private int initialSeats;
        private int totalConcurrentRequests;
        private int successfulDecrements;
        private int failedDecrements;
        private int finalSeatsInDB;
        private boolean oversold;
        private String explanation;
    }

    private Long getOrCreateDemoEventId(int initialSeats) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM events ORDER BY id ASC", (rs, rowNum) -> rs.getLong("id"));
        if (!ids.isEmpty()) {
            Long eventId = ids.get(0);
            jdbcTemplate.update("UPDATE events SET available_seats = ?, total_seats = ? WHERE id = ?", initialSeats, initialSeats, eventId);
            return eventId;
        } else {
            jdbcTemplate.update(
                    "INSERT INTO events (name, venue, event_date, description, total_seats, available_seats, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "Race Demo Event", "Virtual Stage", ZonedDateTime.now().plusDays(7), "Demo Event", initialSeats, initialSeats, ZonedDateTime.now()
            );
            return jdbcTemplate.queryForObject("SELECT id FROM events ORDER BY id DESC", Long.class);
        }
    }

    public DemoResult runUnsafeDemo(int initialSeats, int concurrentRequests) throws InterruptedException {
        final Long eventId = getOrCreateDemoEventId(initialSeats);

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(concurrentRequests, 50));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    // UNSAFE: Read then modify (Race Condition)
                    Integer current = jdbcTemplate.queryForObject(
                            "SELECT available_seats FROM events WHERE id = ?", Integer.class, eventId);

                    if (current != null && current > 0) {
                        // Simulate non-atomic delay making race window wider
                        Thread.sleep(1);
                        jdbcTemplate.update("UPDATE events SET available_seats = ? WHERE id = ?", current - 1, eventId);
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads simultaneously
        endLatch.await();
        executor.shutdown();

        Integer finalSeats = jdbcTemplate.queryForObject("SELECT available_seats FROM events WHERE id = ?", Integer.class, eventId);

        return DemoResult.builder()
                .mode("UNSAFE_READ_MODIFY_WRITE")
                .initialSeats(initialSeats)
                .totalConcurrentRequests(concurrentRequests)
                .successfulDecrements(successCount.get())
                .failedDecrements(failCount.get())
                .finalSeatsInDB(finalSeats != null ? finalSeats : -1)
                .oversold(successCount.get() > initialSeats)
                .explanation("Race condition occurred! Multiple threads read the same stale available_seats value simultaneously. Lost updates caused incorrect final seat count.")
                .build();
    }

    public DemoResult runSafeDemo(int initialSeats, int concurrentRequests) throws InterruptedException {
        final Long eventId = getOrCreateDemoEventId(initialSeats);

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(concurrentRequests, 50));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // SAFE: Atomic SQL decrement with condition
                    int rows = jdbcTemplate.update(
                            "UPDATE events SET available_seats = available_seats - 1 WHERE id = ? AND available_seats > 0", eventId);

                    if (rows > 0) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
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

        Integer finalSeats = jdbcTemplate.queryForObject("SELECT available_seats FROM events WHERE id = ?", Integer.class, eventId);

        return DemoResult.builder()
                .mode("SAFE_ATOMIC_SQL")
                .initialSeats(initialSeats)
                .totalConcurrentRequests(concurrentRequests)
                .successfulDecrements(successCount.get())
                .failedDecrements(failCount.get())
                .finalSeatsInDB(finalSeats != null ? finalSeats : -1)
                .oversold(successCount.get() > initialSeats)
                .explanation("Atomic SQL execution prevented race condition. Database engine serialized row updates safely, resulting in exact inventory count and zero overselling.")
                .build();
    }
}
