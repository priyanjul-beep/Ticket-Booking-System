package com.ticketbooking.unit;

import com.ticketbooking.locking.InMemorySeatLockStrategy;
import com.ticketbooking.locking.LockStrategyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LockingStrategyTest {

    private InMemorySeatLockStrategy lockStrategy;

    @BeforeEach
    void setUp() {
        lockStrategy = new InMemorySeatLockStrategy();
    }

    @Test
    @DisplayName("Should acquire and release single in-memory lock")
    void testTryLockAndUnlock() throws InterruptedException {
        boolean acquired = lockStrategy.tryLock("seat:1", 1, 5, TimeUnit.SECONDS);
        assertTrue(acquired, "Lock should be acquired successfully");

        lockStrategy.unlock("seat:1");

        // Second thread can acquire now
        boolean acquiredAgain = lockStrategy.tryLock("seat:1", 1, 5, TimeUnit.SECONDS);
        assertTrue(acquiredAgain, "Second lock acquisition should succeed after unlock");
        lockStrategy.unlock("seat:1");
    }

    @Test
    @DisplayName("Should prevent concurrent thread from acquiring already held lock")
    void testConcurrentLockRejection() throws InterruptedException, ExecutionException {
        assertTrue(lockStrategy.tryLock("seat:99", 1, 5, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() ->
                lockStrategy.tryLock("seat:99", 100, 1000, TimeUnit.MILLISECONDS)
        );

        Boolean result = future.get();
        assertFalse(result, "Other thread should fail to acquire lock already held");

        lockStrategy.unlock("seat:99");
        executor.shutdown();
    }

    @Test
    @DisplayName("Should sort lock keys to prevent deadlocks and release all on partial failure")
    void testTryLockAll_SortingAndPartialRelease() throws InterruptedException, ExecutionException {
        // Hold lock on seat:2 in another thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> lockStrategy.tryLock("seat:2", 1, 5, TimeUnit.SECONDS)).get();

        // Try to lock seat:2 and seat:1 in reverse order
        List<String> acquired = lockStrategy.tryLockAll(List.of("seat:2", "seat:1"), 100, 1000, TimeUnit.MILLISECONDS);

        // Expect empty list because seat:2 is held
        assertTrue(acquired.isEmpty(), "Partial acquisition failure should result in 0 acquired locks");

        // Verify seat:1 was released despite being lockable
        assertTrue(lockStrategy.tryLock("seat:1", 100, 1000, TimeUnit.MILLISECONDS), "seat:1 should have been un-locked");
        lockStrategy.unlock("seat:1");

        executor.shutdown();
    }

    @Test
    @DisplayName("Should return IN_MEMORY type")
    void testGetType() {
        assertEquals(LockStrategyType.IN_MEMORY, lockStrategy.getType());
    }
}
