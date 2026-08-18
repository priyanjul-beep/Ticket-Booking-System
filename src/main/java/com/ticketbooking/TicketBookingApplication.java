package com.ticketbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ticket Booking System — Production-grade concurrent booking backend.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Java ReentrantLock (in-JVM concurrency)</li>
 *   <li>Pessimistic DB locking (SELECT FOR UPDATE)</li>
 *   <li>Optimistic locking (@Version / CAS)</li>
 *   <li>Redis distributed locking (Redisson)</li>
 *   <li>Idempotency (duplicate request prevention)</li>
 *   <li>Deadlock prevention (sorted lock acquisition)</li>
 *   <li>Flash sale with atomic Redis counters</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class TicketBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketBookingApplication.class, args);
    }
}
