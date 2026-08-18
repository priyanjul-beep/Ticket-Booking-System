package com.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * Seat entity with JPA @Version for Optimistic Locking.
 *
 * OPTIMISTIC LOCKING STRATEGY:
 * ─────────────────────────────
 * The `version` column is a "timestamp" for the row.
 * When two concurrent transactions both read the same seat row,
 * they each hold the same version number (e.g., version=5).
 *
 * Transaction A updates the row:
 *   UPDATE seats SET status='LOCKED', version=6 WHERE id=? AND version=5  ← succeeds
 *
 * Transaction B then tries to update the same row:
 *   UPDATE seats SET status='LOCKED', version=6 WHERE id=? AND version=5  ← fails (version is now 6)
 *
 * JPA throws OptimisticLockException → we catch it, return HTTP 409.
 *
 * WHEN TO USE:
 * - Low-contention scenarios (most users booking different seats)
 * - No DB connection holding needed (non-blocking reads)
 * - Prefer over pessimistic locking when conflict rate < 10%
 *
 * LIMITATION:
 * - High-contention scenarios (flash sales) cause many retries → poor UX
 * - Use pessimistic or Redis lock for high-contention
 */
@Entity
@Table(name = "seats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    /**
     * JPA Optimistic Lock version counter.
     * Incremented automatically by Hibernate on every update.
     * Concurrent updates to the same version will throw OptimisticLockException.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
