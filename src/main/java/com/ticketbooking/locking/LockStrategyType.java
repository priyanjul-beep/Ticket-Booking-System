package com.ticketbooking.locking;

/**
 * Supported locking strategies for demonstration and benchmarking:
 *
 *   IN_MEMORY   — Java ReentrantLock (single-JVM)
 *   PESSIMISTIC — Database row lock (SELECT FOR UPDATE)
 *   OPTIMISTIC  — Database version check (@Version)
 *   REDIS       — Redisson distributed lock (multi-JVM)
 */
public enum LockStrategyType {
    IN_MEMORY,
    PESSIMISTIC,
    OPTIMISTIC,
    REDIS
}
