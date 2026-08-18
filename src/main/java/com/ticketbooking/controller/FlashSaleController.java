package com.ticketbooking.controller;

import com.ticketbooking.dto.CreateFlashSaleRequest;
import com.ticketbooking.dto.FlashSaleDTO;
import com.ticketbooking.dto.FlashSalePurchaseDTO;
import com.ticketbooking.dto.PurchaseFlashSaleRequest;
import com.ticketbooking.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Flash Sale Engine", description = "Ultra-high concurrency flash sale purchasing APIs")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @PostMapping
    @Operation(summary = "Create flash sale campaign")
    public ResponseEntity<FlashSaleDTO> createFlashSale(@Valid @RequestBody CreateFlashSaleRequest request) {
        FlashSaleDTO sale = flashSaleService.createFlashSale(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(sale);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get flash sale campaign status")
    public ResponseEntity<FlashSaleDTO> getFlashSale(@PathVariable Long id) {
        return ResponseEntity.ok(flashSaleService.getFlashSale(id));
    }

    @PostMapping("/{id}/purchase")
    @Operation(summary = "Purchase ticket in flash sale (Redis atomic counter)")
    public ResponseEntity<FlashSalePurchaseDTO> purchaseTicket(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseFlashSaleRequest request) {
        FlashSalePurchaseDTO purchase = flashSaleService.purchaseTicket(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(purchase);
    }
}
