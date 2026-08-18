package com.ticketbooking.service;

import com.ticketbooking.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages event-level inventory updates concurrently safely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final EventRepository eventRepository;

    /**
     * Safe atomic decrement using SQL WHERE clause condition.
     * Prevents race condition where total availableSeats drops below zero.
     */
    @Transactional
    public boolean reserveSeats(Long eventId, int seatCount) {
        int updatedRows = eventRepository.decrementAvailableSeats(eventId, seatCount);
        boolean success = updatedRows > 0;
        if (success) {
            log.info("INVENTORY_RESERVED: {} seats for event {}", seatCount, eventId);
        } else {
            log.warn("INVENTORY_RESERVATION_FAILED: not enough seats for event {}", eventId);
        }
        return success;
    }

    @Transactional
    public void releaseSeats(Long eventId, int seatCount) {
        eventRepository.incrementAvailableSeats(eventId, seatCount);
        log.info("INVENTORY_RELEASED: {} seats returned for event {}", seatCount, eventId);
    }
}
