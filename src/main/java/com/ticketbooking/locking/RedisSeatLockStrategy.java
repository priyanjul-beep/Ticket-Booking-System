package com.ticketbooking.locking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Strategy 4 — Redis Distributed Lock (Redisson).
 *
 * WHAT IT DOES:
 * Coordinates lock acquisition across multiple application nodes (JVM instances).
 * Uses Redisson RLock backed by Redis SET NX PX scripts.
 *
 * FEATURES:
 * - Distributed: Visible to all app instances reading the same Redis.
 * - Automatic Expiry: `leaseTime` ensures locks automatically release if the server crashes while holding a lock (prevents lock leaks).
 * - Wait Time: `waitTime` ensures requests fail fast or wait gracefully instead of blocking indefinitely.
 * - Deadlock Prevention: resource keys sorted prior to acquisition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatLockStrategy implements SeatLockStrategy {

    private final RedissonClient redissonClient;

    private static final String KEY_PREFIX = "lock:seat:";

    @Override
    public boolean tryLock(String resourceId, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        String lockKey = KEY_PREFIX + resourceId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
        if (acquired) {
            log.debug("REDIS_LOCK_ACQUIRED for {}", lockKey);
        } else {
            log.warn("REDIS_LOCK_FAILED for {}", lockKey);
        }
        return acquired;
    }

    @Override
    public void unlock(String resourceId) {
        String lockKey = KEY_PREFIX + resourceId;
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("REDIS_LOCK_RELEASED for {}", lockKey);
        }
    }

    @Override
    public List<String> tryLockAll(List<String> resourceIds, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        List<String> sortedIds = new ArrayList<>(resourceIds);
        Collections.sort(sortedIds);

        List<String> acquiredLocks = new ArrayList<>();
        for (String id : sortedIds) {
            boolean success = tryLock(id, waitTime, leaseTime, unit);
            if (success) {
                acquiredLocks.add(id);
            } else {
                log.warn("REDIS_PARTIAL_LOCK_FAILURE on {}, releasing {} acquired locks", id, acquiredLocks.size());
                unlockAll(acquiredLocks);
                return Collections.emptyList();
            }
        }
        return acquiredLocks;
    }

    @Override
    public void unlockAll(List<String> resourceIds) {
        for (String id : resourceIds) {
            try {
                unlock(id);
            } catch (Exception e) {
                log.error("Failed to release Redis lock for {}", id, e);
            }
        }
    }

    @Override
    public LockStrategyType getType() {
        return LockStrategyType.REDIS;
    }
}
