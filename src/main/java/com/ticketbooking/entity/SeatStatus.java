package com.ticketbooking.entity;

/**
 * Seat status lifecycle:
 *
 *   AVAILABLE ──→ LOCKED ──→ BOOKED
 *                   │
 *                   └──(expiry/failure)──→ AVAILABLE
 *
 * Concurrent state transitions are protected by locking strategies.
 */
public enum SeatStatus {
    AVAILABLE,
    LOCKED,
    BOOKED
}
