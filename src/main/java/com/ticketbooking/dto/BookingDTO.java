package com.ticketbooking.dto;

import com.ticketbooking.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDTO {
    private Long id;
    private String bookingReference;
    private Long userId;
    private Long eventId;
    private BookingStatus status;
    private BigDecimal totalAmount;
    private String idempotencyKey;
    private List<SeatDTO> seats;
    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;
    private ZonedDateTime confirmedAt;
}
