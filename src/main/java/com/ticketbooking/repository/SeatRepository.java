package com.ticketbooking.repository;

import com.ticketbooking.entity.Seat;
import com.ticketbooking.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventId(Long eventId);

    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);

    /**
     * PESSIMISTIC WRITE LOCK — SELECT ... FOR UPDATE
     *
     * WHAT IT DOES:
     *   Acquires an exclusive row-level lock in PostgreSQL.
     *   All other transactions trying to read/write this row will WAIT
     *   until the current transaction commits or rolls back.
     *
     * FLOW:
     *   Transaction A: SELECT * FROM seats WHERE id=? FOR UPDATE  → gets lock
     *   Transaction B: SELECT * FROM seats WHERE id=? FOR UPDATE  → WAITS
     *   Transaction A: UPDATE seats SET status='LOCKED' ...       → commits
     *   Transaction B: now proceeds, reads status='LOCKED', fails gracefully
     *
     * WHY USE THIS?
     *   Guarantees exactly one writer at a time. No optimistic retry needed.
     *   Best for high-contention scenarios where conflicts are frequent.
     *
     * LIMITATION:
     *   Holds a DB connection for the duration → reduces connection pool availability.
     *   Can cause timeouts if lock-holder is slow.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdWithPessimisticLock(@Param("id") Long id);

    /**
     * Pessimistic lock on multiple seats — used when booking multiple seats.
     * IDs must be sorted before calling to prevent deadlocks:
     *   Thread A locks seat [1,2], Thread B locks seat [1,2] — same order → no deadlock
     *   Thread A locks [1,2], Thread B locks [2,1] → circular wait → DEADLOCK
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id IN :ids ORDER BY s.id ASC")
    List<Seat> findAllByIdWithPessimisticLock(@Param("ids") List<Long> ids);

    @Modifying
    @Query("UPDATE Seat s SET s.status = :status WHERE s.id IN :ids AND s.status = :expectedStatus")
    int updateStatusForSeats(@Param("ids") List<Long> ids,
                             @Param("status") SeatStatus status,
                             @Param("expectedStatus") SeatStatus expectedStatus);

    @Query("SELECT s FROM Seat s WHERE s.id IN :ids")
    List<Seat> findAllByIds(@Param("ids") List<Long> ids);
}
