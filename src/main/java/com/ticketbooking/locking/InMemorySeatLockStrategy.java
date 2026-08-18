package com.ticketbooking.locking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Strategy 1 — Java ReentrantLock (In-Memory).
 *
 * WHAT IT DOES:
 * Maintain a ConcurrentHashMap of resource keys to Java ReentrantLock objects.
 * Uses lock striping via computeIfAbsent to dynamically create locks per seat.
 *
 * DEADLOCK PREVENTION:
 * When locking multiple seats, resource keys are sorted lexicographically before acquiring.
 * Request A (Seat 1, Seat 2) -> locks "seat:1", then "seat:2"
 * Request B (Seat 2, Seat 1) -> sorted to ["seat:1", "seat:2"] -> locks "seat:1", then "seat:2"
 * Circular wait condition is impossible.
 *
 * LIMITATION:
 * Works ONLY inside a single JVM. If application scales to N instances,
 * instance 1 and instance 2 have separate lock maps in memory, allowing double booking.
 */
@Slf4j
@Component
public class InMemorySeatLockStrategy implements SeatLockStrategy {

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String resourceId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        ReentrantLock lock = lockMap.computeIfAbsent(resourceId, k -> new ReentrantLock(true)); // fair lock
        boolean acquired = lock.tryLock(waitTime, unit);
        if (acquired) {
            log.debug("IN_MEMORY_LOCK_ACQUIRED for {}", resourceId);
        } else {
            log.warn("IN_MEMORY_LOCK_FAILED for {}", resourceId);
        }
        return acquired;
    }

    @Override
    public void unlock(String resourceId) {
        ReentrantLock lock = lockMap.get(resourceId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("IN_MEMORY_LOCK_RELEASED for {}", resourceId);
        }
    }

    @Override
    public List<String> tryLockAll(List<String> resourceIds, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        // SORT to prevent deadlocks (global lock ordering)
        List<String> sortedIds = new ArrayList<>(resourceIds);
        Collections.sort(sortedIds);

        List<String> acquiredLocks = new ArrayList<>();
        for (String id : sortedIds) {
            boolean success = tryLock(id, waitTime, leaseTime, unit);
            if (success) {
                acquiredLocks.add(id);
            } else {
                // Partial failure -> rollback all previously acquired locks in this batch
                log.warn("IN_MEMORY_PARTIAL_LOCK_FAILURE on {}, releasing {} acquired locks", id, acquiredLocks.size());
                unlockAll(acquiredLocks);
                return Collections.emptyList();
            }
        }
        return acquiredLocks;
    }

    @Override
    public void unlockAll(List<String> resourceIds) {
        for (String id : resourceIds) {
            unlock(id);
        }
    }

    @Override
    public LockStrategyType getType() {
        return LockStrategyType.IN_MEMORY;
    }
}
