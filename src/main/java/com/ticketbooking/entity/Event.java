package com.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "event_date", nullable = false)
    private ZonedDateTime eventDate;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    /**
     * availableSeats must only be decremented inside a transaction with
     * pessimistic or optimistic locking to prevent race conditions.
     *
     * UNSAFE:  event.setAvailableSeats(event.getAvailableSeats() - 1)
     *          → read-modify-write outside a lock = lost update problem
     *
     * SAFE:    UPDATE events SET available_seats = available_seats - 1
     *          WHERE id = ? AND available_seats > 0
     *          (atomic SQL, or guarded by row-level lock)
     */
    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now();
        if (this.eventDate == null) {
            this.eventDate = ZonedDateTime.now().plusDays(7);
        }
    }
}
