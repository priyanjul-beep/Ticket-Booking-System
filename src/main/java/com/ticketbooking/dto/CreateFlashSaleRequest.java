package com.ticketbooking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
public class CreateFlashSaleRequest {
    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotBlank(message = "Sale name is required")
    private String name;

    @NotNull(message = "Total tickets is required")
    @Min(value = 1, message = "Total tickets must be at least 1")
    private Integer totalTickets;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    @NotNull(message = "Start time is required")
    private ZonedDateTime startsAt;

    @NotNull(message = "End time is required")
    @Future(message = "End time must be in the future")
    private ZonedDateTime endsAt;
}
