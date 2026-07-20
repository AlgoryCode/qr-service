package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.UserEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {

    @Query("""
            select entitlement
            from UserEntitlement entitlement
            where entitlement.userId = :userId
              and (entitlement.unlimited = true or entitlement.remainingQuantity > :remainingQuantity)
            order by entitlement.createdAt asc
            """)
    List<UserEntitlement> findUsableByUserIdOrderByCreatedAtAsc(
            @Param("userId") Long userId,
            @Param("remainingQuantity") Integer remainingQuantity
    );

    List<UserEntitlement> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserEntitlement> findByPurchaseIdOrderByProductCodeAsc(Long purchaseId);

    Optional<UserEntitlement> findByPurchaseIdAndProductId(Long purchaseId, Long productId);

    boolean existsByProductId(Long productId);
}
