package com.ticketbooking.locking;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Common interface for seat locking abstractions.
 * Allows switching seamlessly between In-Memory, Redis Distributed Lock,
 * and Database-level locking strategies.
 */
public interface SeatLockStrategy {

    /**
     * Try to acquire a lock for a single resource.
     *
     * @param resourceId unique resource identifier (e.g., "seat:123")
     * @param waitTime   maximum time to wait for lock acquisition
     * @param leaseTime  maximum time lock is held before auto-release
     * @param unit       time unit
     * @return true if lock acquired successfully, false otherwise
     */
    boolean tryLock(String resourceId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Release lock for a single resource safely.
     *
     * @param resourceId unique resource identifier
     */
    void unlock(String resourceId);

    /**
     * Try to acquire locks for multiple resources in deterministic order.
     * Guaranteed deadlock-free: resources are sorted before locking.
     *
     * @param resourceIds list of resource identifiers
     * @param waitTime    wait time per lock
     * @param leaseTime   lease time per lock
     * @param unit        time unit
     * @return list of successfully locked resource IDs (empty if acquisition failed halfway)
     */
    List<String> tryLockAll(List<String> resourceIds, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Unlock all acquired resources.
     *
     * @param resourceIds list of resource identifiers
     */
    void unlockAll(List<String> resourceIds);

    /**
     * Type identifier of this strategy.
     */
    LockStrategyType getType();
}
