package com.ticketbooking.repository;

import com.ticketbooking.entity.FlashSalePurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlashSalePurchaseRepository extends JpaRepository<FlashSalePurchase, Long> {
    List<FlashSalePurchase> findByFlashSaleId(Long flashSaleId);
    List<FlashSalePurchase> findByUserId(Long userId);
    long countByFlashSaleId(Long flashSaleId);
}
