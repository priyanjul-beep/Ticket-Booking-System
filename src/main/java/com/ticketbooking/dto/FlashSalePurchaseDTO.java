package com.ticketbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSalePurchaseDTO {
    private Long id;
    private Long flashSaleId;
    private Long userId;
    private Integer quantity;
    private String purchaseRef;
    private String status;
    private ZonedDateTime createdAt;
}
