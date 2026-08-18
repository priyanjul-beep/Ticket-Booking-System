package com.ticketbooking.repository;

import com.ticketbooking.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Atomically decrements availableSeats by 1.
     *
     * WHY ATOMIC SQL INSTEAD OF READ-MODIFY-WRITE?
     * ─────────────────────────────────────────────
     * UNSAFE (race condition):
     *   int seats = event.getAvailableSeats();  // T1 reads 1
     *   event.setAvailableSeats(seats - 1);      // T1 writes 0
     *   // T2 also reads 1 before T1 commits → both write 0 → oversold
     *
     * SAFE (atomic SQL):
     *   UPDATE events SET available_seats = available_seats - 1
     *   WHERE id = ? AND available_seats > 0
     *   → Database executes as one atomic operation, preventing the lost update.
     *
     * Returns number of rows updated (1 = success, 0 = no seats left).
     */
    @Modifying
    @Query("UPDATE Event e SET e.availableSeats = e.availableSeats - :count " +
           "WHERE e.id = :eventId AND e.availableSeats >= :count")
    int decrementAvailableSeats(@Param("eventId") Long eventId, @Param("count") int count);

    @Modifying
    @Query("UPDATE Event e SET e.availableSeats = e.availableSeats + :count " +
           "WHERE e.id = :eventId")
    int incrementAvailableSeats(@Param("eventId") Long eventId, @Param("count") int count);
}
