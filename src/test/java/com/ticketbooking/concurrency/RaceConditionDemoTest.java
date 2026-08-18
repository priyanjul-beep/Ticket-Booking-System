package com.ticketbooking.concurrency;

import com.ticketbooking.entity.Event;
import com.ticketbooking.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RaceConditionDemoTest {

    @Autowired private RaceConditionDemo raceConditionDemo;
    @Autowired private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        if (!eventRepository.existsById(1L)) {
            eventRepository.save(Event.builder()
                    .id(1L)
                    .name("Race Demo Event")
                    .venue("Lab")
                    .eventDate(java.time.ZonedDateTime.now().plusDays(7))
                    .totalSeats(100)
                    .availableSeats(100)
                    .build());
        }
    }

    @Test
    @DisplayName("Unsafe Demo should execute and report read-modify-write results")
    void testUnsafeDemoExecution() throws InterruptedException {
        RaceConditionDemo.DemoResult result = raceConditionDemo.runUnsafeDemo(10, 50);
        assertNotNull(result);
        assertEquals("UNSAFE_READ_MODIFY_WRITE", result.getMode());
        assertEquals(50, result.getTotalConcurrentRequests());
    }

    @Test
    @DisplayName("Safe Demo should execute atomic SQL updates with zero overselling")
    void testSafeDemoExecution() throws InterruptedException {
        RaceConditionDemo.DemoResult result = raceConditionDemo.runSafeDemo(10, 50);
        assertNotNull(result);
        assertEquals("SAFE_ATOMIC_SQL", result.getMode());
        assertEquals(10, result.getSuccessfulDecrements());
        assertEquals(40, result.getFailedDecrements());
        assertEquals(0, result.getFinalSeatsInDB());
        assertFalse(result.isOversold());
    }
}
