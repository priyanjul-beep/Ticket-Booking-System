package com.ticketbooking.scheduler;

import com.ticketbooking.entity.Booking;
import com.ticketbooking.entity.BookingSeat;
import com.ticketbooking.entity.BookingStatus;
import com.ticketbooking.entity.Seat;
import com.ticketbooking.entity.SeatStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Background Scheduler — Booking Expiration Engine.
 *
 * Runs every 60 seconds to scan for PENDING bookings whose `expiresAt`
 * timestamp has passed without payment confirmation.
 *
 * ACTIONS TAKEN:
 * 1. Change Booking status: PENDING -> EXPIRED
 * 2. Change Seat status:    LOCKED  -> AVAILABLE
 * 3. Restore Event inventory: increment availableSeats
 *
 * CONCURRENCY PROTECTION:
 * Uses atomic conditional updates (`updateStatusIfExpected`) to ensure
 * a concurrent payment confirmation happening at the same second cannot
 * race with expiration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;

    @Scheduled(fixedRate = 60000) // Runs every 60 seconds
    @Transactional
    public void cleanupExpiredBookings() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Booking> expiredBookings = bookingRepository.findExpiredPendingBookings(now);

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("EXPIRATION_SCHEDULER_START: Found {} expired pending bookings", expiredBookings.size());

        for (Booking booking : expiredBookings) {
            try {
                expireBooking(booking);
            } catch (Exception e) {
                log.error("Failed to expire booking ID {}", booking.getId(), e);
            }
        }
    }

    @Transactional
    public void expireBooking(Booking booking) {
        // Atomic status check-and-set PENDING -> EXPIRED
        int updated = bookingRepository.updateStatusIfExpected(booking.getId(), BookingStatus.EXPIRED, BookingStatus.PENDING);
        if (updated == 0) {
            log.info("Booking ID {} was already updated by payment service, skipping expiration", booking.getId());
            return;
        }

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
        List<Seat> seats = bookingSeats.stream().map(BookingSeat::getSeat).toList();

        // Release seats back to AVAILABLE
        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.LOCKED) {
                seat.setStatus(SeatStatus.AVAILABLE);
            }
        }
        seatRepository.saveAll(seats);

        // Increment event available seats counter
        eventRepository.incrementAvailableSeats(booking.getEvent().getId(), seats.size());

        log.info("EXPIRED_BOOKING_CLEANUP_SUCCESS: Booking ID {}, Ref {}, Seats released: {}",
                booking.getId(), booking.getBookingReference(), seats.size());
    }
}
