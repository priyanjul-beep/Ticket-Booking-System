package com.ticketbooking.controller;

import com.ticketbooking.dto.BookingDTO;
import com.ticketbooking.dto.CreateBookingRequest;
import com.ticketbooking.payment.PaymentOutcome;
import com.ticketbooking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Engine", description = "High-concurrency ticket reservation APIs")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create booking with specified locking strategy and idempotency key")
    public ResponseEntity<BookingDTO> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        BookingDTO booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking status by ID")
    public ResponseEntity<BookingDTO> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Process payment for a PENDING booking")
    public ResponseEntity<BookingDTO> processPayment(
            @PathVariable Long id,
            @RequestParam(required = false) PaymentOutcome outcome) {
        BookingDTO booking = bookingService.processBookingPayment(id, outcome);
        return ResponseEntity.ok(booking);
    }
}
