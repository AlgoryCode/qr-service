package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.enums.ProductCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {

    List<UserEntitlement> findByUserIdAndRemainingQuantityGreaterThanOrderByCreatedAtAsc(
            Long userId,
            Integer remainingQuantity
    );

    List<UserEntitlement> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserEntitlement> findByPurchaseIdOrderByProductCodeAsc(Long purchaseId);

    Optional<UserEntitlement> findByPurchaseIdAndProductId(Long purchaseId, Long productId);
}
