package com.ticketbooking.entity;

/**
 * Booking status state machine:
 *
 *   PENDING ──→ CONFIRMED  (payment success)
 *   PENDING ──→ CANCELLED  (payment failure / user cancels)
 *   PENDING ──→ EXPIRED    (TTL exceeded, scheduler triggers)
 *   PENDING ──→ FAILED     (system error)
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    FAILED
}
