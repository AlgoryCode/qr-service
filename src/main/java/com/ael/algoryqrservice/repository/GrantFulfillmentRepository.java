package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GrantFulfillmentRepository extends JpaRepository<GrantFulfillment, Long> {

    Optional<GrantFulfillment> findByPurchaseId(Long purchaseId);

    boolean existsByUserId(Long userId);

    List<GrantFulfillment> findByUserIdAndStatus(Long userId, GrantFulfillmentStatus status);

    @Query("SELECT f FROM GrantFulfillment f WHERE f.userId = :userId AND f.packageId = :packageId AND f.status = :status")
    List<GrantFulfillment> findByUserIdAndPackageIdAndStatus(
            @Param("userId") Long userId,
            @Param("packageId") Long packageId,
            @Param("status") GrantFulfillmentStatus status
    );

    @Query("SELECT f FROM GrantFulfillment f WHERE f.userId = :userId AND f.status = 'ACTIVE' AND f.expiresAt IS NOT NULL AND f.expiresAt < CURRENT_TIMESTAMP")
    List<GrantFulfillment> findExpiredActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT f.userId FROM GrantFulfillment f WHERE f.status = 'ACTIVE' AND f.expiresAt IS NOT NULL AND f.expiresAt < CURRENT_TIMESTAMP")
    List<Long> findDistinctUserIdsWithExpiredFulfillments();

    @Query("SELECT f FROM GrantFulfillment f WHERE f.status = 'ACTIVE' AND f.expiresAt IS NOT NULL AND f.expiresAt < CURRENT_TIMESTAMP")
    List<GrantFulfillment> findAllExpiredActive();

    @Query("SELECT f FROM GrantFulfillment f WHERE f.paymentId IS NOT NULL AND f.purchaseId IN " +
           "(SELECT p.id FROM Purchase p WHERE p.status = 'ACTIVE' AND p.paymentId IS NOT NULL) " +
           "AND NOT EXISTS (SELECT d FROM FulfillmentDetail d WHERE d.fulfillmentId = f.id)")
    List<GrantFulfillment> findFulfillmentsWithMissingDetails();
}
