package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    List<PurchaseItem> findByPurchaseId(Long purchaseId);

    List<PurchaseItem> findByPurchaseIdIn(List<Long> purchaseIds);

    boolean existsByPurchaseId(Long purchaseId);
}
