package com.ticketbooking.dto;

import com.ticketbooking.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO {
    private Long id;
    private Long eventId;
    private String seatNumber;
    private String category;
    private BigDecimal price;
    private SeatStatus status;
    private Long version;
}
