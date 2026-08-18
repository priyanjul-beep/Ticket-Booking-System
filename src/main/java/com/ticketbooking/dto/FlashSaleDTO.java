package com.ticketbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleDTO {
    private Long id;
    private Long eventId;
    private String name;
    private Integer totalTickets;
    private Integer soldTickets;
    private Integer remainingTickets;
    private BigDecimal price;
    private ZonedDateTime startsAt;
    private ZonedDateTime endsAt;
    private Boolean active;
}
