package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByUserIdOrderByPurchasedAtDesc(Long userId);

    List<Purchase> findByStatusAndExpiresAtBefore(PurchaseStatus status, LocalDateTime expiresAt);

    List<Purchase> findByStatusAndPurchasedAtBefore(PurchaseStatus status, LocalDateTime purchasedAt);
}
