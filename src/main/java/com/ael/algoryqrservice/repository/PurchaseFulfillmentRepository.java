package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PurchaseFulfillment;
import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseFulfillmentRepository extends JpaRepository<PurchaseFulfillment, Long> {

    Optional<PurchaseFulfillment> findByPurchaseIdAndInstallmentId(Long purchaseId, String installmentId);

    List<PurchaseFulfillment> findByPurchaseIdAndStatusOrderByInstallmentNumberAsc(
            Long purchaseId,
            FulfillmentStatus status
    );

    List<PurchaseFulfillment> findByPurchaseIdOrderByInstallmentNumberAsc(Long purchaseId);

    boolean existsByPurchaseIdInAndStatus(Collection<Long> purchaseIds, FulfillmentStatus status);
}
