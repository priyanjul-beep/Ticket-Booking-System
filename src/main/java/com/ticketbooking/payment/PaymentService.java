package com.ticketbooking.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated Payment Service.
 *
 * Demonstrates how booking state machine handles:
 * - SUCCESS: PENDING -> CONFIRMED
 * - FAILURE: PENDING -> CANCELLED (seats released back to AVAILABLE)
 * - TIMEOUT: PENDING -> EXPIRED (seats released back to AVAILABLE)
 */
@Slf4j
@Service
public class PaymentService {

    public PaymentOutcome processPayment(String bookingReference, BigDecimal amount) {
        log.info("PAYMENT_STARTED for ref: {}, amount: {}", bookingReference, amount);

        // Simulated network delay
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 90% success, 5% failure, 5% timeout simulation
        int dice = ThreadLocalRandom.current().nextInt(100);
        if (dice < 90) {
            log.info("PAYMENT_SUCCESS for ref: {}", bookingReference);
            return PaymentOutcome.SUCCESS;
        } else if (dice < 95) {
            log.warn("PAYMENT_FAILED for ref: {}", bookingReference);
            return PaymentOutcome.FAILURE;
        } else {
            log.warn("PAYMENT_TIMEOUT for ref: {}", bookingReference);
            return PaymentOutcome.TIMEOUT;
        }
    }

    public PaymentOutcome processPaymentWithOutcome(String bookingReference, BigDecimal amount, PaymentOutcome forcedOutcome) {
        log.info("PAYMENT_STARTED (forced {}) for ref: {}", forcedOutcome, bookingReference);
        return forcedOutcome;
    }
}
