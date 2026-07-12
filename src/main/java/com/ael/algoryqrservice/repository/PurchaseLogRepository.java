package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PurchaseLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseLogRepository extends JpaRepository<PurchaseLog, Long> {

    List<PurchaseLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PurchaseLog> findByPurchaseIdOrderByCreatedAtDesc(Long purchaseId);
}
