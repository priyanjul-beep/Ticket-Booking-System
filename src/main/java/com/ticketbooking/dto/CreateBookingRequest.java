package com.ticketbooking.dto;

import com.ticketbooking.locking.LockStrategyType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateBookingRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotEmpty(message = "At least one seat ID must be selected")
    private List<Long> seatIds;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    /**
     * Locking strategy override for benchmarking/testing.
     * Default: REDIS
     */
    private LockStrategyType strategy = LockStrategyType.REDIS;
}
