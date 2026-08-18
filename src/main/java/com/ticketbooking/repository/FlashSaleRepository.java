package com.ticketbooking.repository;

import com.ticketbooking.entity.FlashSale;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FlashSale f WHERE f.id = :id")
    Optional<FlashSale> findByIdWithLock(@Param("id") Long id);

    /**
     * Atomic increment of soldTickets only when capacity remains.
     * Returns 1 on success, 0 when sold out.
     *
     * This is the DB-layer safety net. The primary guard is the Redis
     * atomic counter — this prevents any edge-case DB overselling.
     */
    @Modifying
    @Query("UPDATE FlashSale f SET f.soldTickets = f.soldTickets + 1 " +
           "WHERE f.id = :id AND f.soldTickets < f.totalTickets AND f.active = true")
    int incrementSoldTickets(@Param("id") Long id);
}
