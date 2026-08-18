package com.ticketbooking.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class BookingMetricsService {

    private final Counter bookingSuccessCounter;
    private final Counter bookingFailureCounter;
    private final Counter lockAcquisitionCounter;
    private final Counter lockFailureCounter;
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Timer bookingLatencyTimer;

    public BookingMetricsService(MeterRegistry registry) {
        this.bookingSuccessCounter = Counter.builder("booking_success_total")
                .description("Total successful bookings")
                .register(registry);

        this.bookingFailureCounter = Counter.builder("booking_failure_total")
                .description("Total failed bookings")
                .register(registry);

        this.lockAcquisitionCounter = Counter.builder("seat_lock_acquisition_total")
                .description("Total successful seat lock acquisitions")
                .register(registry);

        this.lockFailureCounter = Counter.builder("seat_lock_failure_total")
                .description("Total failed seat lock acquisitions")
                .register(registry);

        this.paymentSuccessCounter = Counter.builder("payment_success_total")
                .description("Total successful payments")
                .register(registry);

        this.paymentFailureCounter = Counter.builder("payment_failure_total")
                .description("Total failed payments")
                .register(registry);

        this.bookingLatencyTimer = Timer.builder("booking_latency")
                .description("Booking latency timing")
                .register(registry);
    }

    public void recordBookingSuccess() { bookingSuccessCounter.increment(); }
    public void recordBookingFailure() { bookingFailureCounter.increment(); }
    public void recordLockAcquired()   { lockAcquisitionCounter.increment(); }
    public void recordLockFailed()     { lockFailureCounter.increment(); }
    public void recordPaymentSuccess() { paymentSuccessCounter.increment(); }
    public void recordPaymentFailure() { paymentFailureCounter.increment(); }

    public void recordLatency(long durationMillis) {
        bookingLatencyTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }
}
