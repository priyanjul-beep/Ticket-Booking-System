package com.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Stores the result of every processed idempotent request.
 *
 * IDEMPOTENCY FLOW:
 * ─────────────────
 * 1. Client sends POST /api/bookings with header Idempotency-Key: <uuid>
 * 2. System checks Redis first (fast path) — if key exists, return cached response
 * 3. If not in Redis, check this DB table (durable path)
 * 4. If new key, process the request and save result here
 * 5. Concurrent duplicate requests: DB unique constraint prevents double-save;
 *    second writer catches DataIntegrityViolationException → returns cached result
 *
 * WHY BOTH REDIS AND DB?
 * - Redis: ultra-fast lookup (sub-millisecond) for hot recent requests
 * - DB: durable storage so idempotency survives Redis restarts/eviction
 */
@Entity
@Table(name = "idempotency_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
