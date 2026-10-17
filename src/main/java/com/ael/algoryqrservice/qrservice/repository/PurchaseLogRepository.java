package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.PurchaseLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseLogRepository extends JpaRepository<PurchaseLog, Long> {

    List<PurchaseLog> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    List<PurchaseLog> findByPurchaseIdOrderByCreatedAtDesc(Long purchaseId);
}
