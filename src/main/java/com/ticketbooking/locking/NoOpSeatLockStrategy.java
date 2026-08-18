package com.ticketbooking.locking;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Strategy 2 & 3 (Pessimistic & Optimistic DB Locking).
 *
 * For DB-level locking, application-level pre-locking is a No-Op.
 * The locking happens inside the JPA transaction via SELECT FOR UPDATE (Pessimistic)
 * or @Version check on SQL update (Optimistic).
 */
@Component
public class NoOpSeatLockStrategy implements SeatLockStrategy {

    @Override
    public boolean tryLock(String resourceId, long waitTime, long leaseTime, TimeUnit unit) {
        return true;
    }

    @Override
    public void unlock(String resourceId) {
        // No-Op
    }

    @Override
    public List<String> tryLockAll(List<String> resourceIds, long waitTime, long leaseTime, TimeUnit unit) {
        List<String> sorted = new ArrayList<>(resourceIds);
        Collections.sort(sorted);
        return sorted;
    }

    @Override
    public void unlockAll(List<String> resourceIds) {
        // No-Op
    }

    @Override
    public LockStrategyType getType() {
        return LockStrategyType.PESSIMISTIC; // Also used for OPTIMISTIC
    }
}
