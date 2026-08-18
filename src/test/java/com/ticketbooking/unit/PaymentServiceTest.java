package com.ticketbooking.unit;

import com.ticketbooking.payment.PaymentOutcome;
import com.ticketbooking.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
    }

    @Test
    @DisplayName("Should return forced outcome when specified")
    void testProcessPaymentWithForcedOutcome() {
        PaymentOutcome outcome = paymentService.processPaymentWithOutcome("REF-123", new BigDecimal("100.00"), PaymentOutcome.SUCCESS);
        assertEquals(PaymentOutcome.SUCCESS, outcome);

        PaymentOutcome failOutcome = paymentService.processPaymentWithOutcome("REF-123", new BigDecimal("100.00"), PaymentOutcome.FAILURE);
        assertEquals(PaymentOutcome.FAILURE, failOutcome);

        PaymentOutcome timeoutOutcome = paymentService.processPaymentWithOutcome("REF-123", new BigDecimal("100.00"), PaymentOutcome.TIMEOUT);
        assertEquals(PaymentOutcome.TIMEOUT, timeoutOutcome);
    }

    @Test
    @DisplayName("Should return non-null outcome during standard payment call")
    void testProcessPayment() {
        PaymentOutcome outcome = paymentService.processPayment("REF-456", new BigDecimal("75.00"));
        assertNotNull(outcome);
    }
}
