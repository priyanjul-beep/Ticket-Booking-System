package com.ticketbooking.repository;

import com.ticketbooking.entity.Booking;
import com.ticketbooking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findByUserId(Long userId);

    /**
     * Find all PENDING bookings past their expiry time.
     * Called by BookingExpirationScheduler every 60 seconds.
     *
     * Uses index: idx_bookings_expires_at (partial index on status='PENDING')
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.expiresAt < :now")
    List<Booking> findExpiredPendingBookings(@Param("now") ZonedDateTime now);

    @Modifying
    @Query("UPDATE Booking b SET b.status = :newStatus WHERE b.id = :id AND b.status = :expectedStatus")
    int updateStatusIfExpected(@Param("id") Long id,
                                @Param("newStatus") BookingStatus newStatus,
                                @Param("expectedStatus") BookingStatus expectedStatus);
}
