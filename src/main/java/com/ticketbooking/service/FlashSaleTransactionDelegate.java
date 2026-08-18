package com.ticketbooking.service;

import com.ticketbooking.dto.FlashSalePurchaseDTO;
import com.ticketbooking.dto.PurchaseFlashSaleRequest;
import com.ticketbooking.entity.FlashSale;
import com.ticketbooking.entity.FlashSalePurchase;
import com.ticketbooking.entity.User;
import com.ticketbooking.exception.FlashSaleSoldOutException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.repository.FlashSalePurchaseRepository;
import com.ticketbooking.repository.FlashSaleRepository;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.util.BookingReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

/**
 * Transactional Delegate for Flash Sale DB Purchases.
 *
 * Prevents Spring @Transactional self-invocation bypass when called from FlashSaleService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleTransactionDelegate {

    private final FlashSaleRepository flashSaleRepository;
    private final FlashSalePurchaseRepository purchaseRepository;
    private final UserRepository userRepository;

    @Transactional
    public FlashSalePurchaseDTO processPurchaseInDB(Long flashSaleId, PurchaseFlashSaleRequest request) {
        FlashSale sale = flashSaleRepository.findById(flashSaleId)
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found: " + flashSaleId));

        if (!sale.getActive() || ZonedDateTime.now().isBefore(sale.getStartsAt()) || ZonedDateTime.now().isAfter(sale.getEndsAt())) {
            throw new IllegalStateException("Flash sale is not currently active");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));

        // Atomic DB check & increment
        int updated = flashSaleRepository.incrementSoldTickets(flashSaleId);
        if (updated == 0) {
            throw new FlashSaleSoldOutException("Flash sale sold out in database check!");
        }

        FlashSalePurchase purchase = FlashSalePurchase.builder()
                .flashSale(sale)
                .user(user)
                .quantity(request.getQuantity())
                .purchaseRef(BookingReferenceGenerator.generateFlashPurchaseReference())
                .status("COMPLETED")
                .build();

        FlashSalePurchase saved = purchaseRepository.save(purchase);
        log.info("FLASH_SALE_PURCHASE_SUCCESS: Ref {}, Sale ID {}, User ID {}", saved.getPurchaseRef(), flashSaleId, user.getId());

        return mapPurchaseToDTO(saved);
    }

    private FlashSalePurchaseDTO mapPurchaseToDTO(FlashSalePurchase purchase) {
        return FlashSalePurchaseDTO.builder()
                .id(purchase.getId())
                .flashSaleId(purchase.getFlashSale().getId())
                .userId(purchase.getUser().getId())
                .quantity(purchase.getQuantity())
                .purchaseRef(purchase.getPurchaseRef())
                .status(purchase.getStatus())
                .createdAt(purchase.getCreatedAt())
                .build();
    }
}
