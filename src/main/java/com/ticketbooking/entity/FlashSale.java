package com.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * FlashSale entity.
 *
 * CONCURRENCY STRATEGY FOR FLASH SALES:
 * ──────────────────────────────────────
 * Flash sales involve extreme contention (thousands of users, 100 tickets).
 * Using a DB row lock for every request would create a massive queue at the DB.
 *
 * Preferred approach:
 *   1. Use Redis DECR (atomic) to decrement the available count
 *   2. Only if Redis DECR > 0 (i.e., tickets remain), proceed to DB
 *   3. DB transaction saves the purchase record
 *   4. On failure/timeout, INCR the Redis counter back
 *
 * This keeps 99% of load on Redis (in-memory, nanosecond latency)
 * and only involves PostgreSQL for actual committed purchases.
 *
 * The @Version field provides a safety net for the DB layer.
 */
@Entity
@Table(name = "flash_sales")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FlashSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "total_tickets", nullable = false)
    private Integer totalTickets;

    @Column(name = "sold_tickets", nullable = false)
    private Integer soldTickets;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "starts_at", nullable = false)
    private ZonedDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private ZonedDateTime endsAt;

    @Column(nullable = false)
    private Boolean active;

    @Version
    @Column(nullable = false)
    private Long version;
}
