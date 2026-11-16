package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.EntitlementSource;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.UserEntitlementEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserEntitlementJpaRepository extends JpaRepository<UserEntitlementEntity, Long> {

    @Query("""
            select e from UserEntitlementEntity e
            where e.merchantId = :merchantId
              and e.status = :status
              and (e.expiresAt is null or e.expiresAt > :now)
            """)
    List<UserEntitlementEntity> findActiveByMerchantId(
            @Param("merchantId") Long merchantId,
            @Param("status") PackageStatus status,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from UserEntitlementEntity e
            where e.merchantId = :merchantId
              and e.status = :status
              and (e.expiresAt is null or e.expiresAt > :now)
            order by e.id
            """)
    List<UserEntitlementEntity> lockActiveByMerchantId(
            @Param("merchantId") Long merchantId,
            @Param("status") PackageStatus status,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
            update UserEntitlementEntity e
            set e.expiresAt = :expiresAt, e.updatedAt = :updatedAt
            where e.purchaseId = :purchaseId
              and e.source = :source
              and e.status = :status
            """)
    int extendExpiry(
            @Param("purchaseId") Long purchaseId,
            @Param("source") EntitlementSource source,
            @Param("status") PackageStatus status,
            @Param("expiresAt") Instant expiresAt,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying
    @Query("""
            update UserEntitlementEntity e
            set e.status = :newStatus, e.updatedAt = :updatedAt
            where e.merchantId = :merchantId
              and e.status = :currentStatus
              and e.fulfillmentId <> :keepFulfillmentId
              and e.source <> :keptSource
            """)
    int supersedeOthers(
            @Param("merchantId") Long merchantId,
            @Param("keepFulfillmentId") Long keepFulfillmentId,
            @Param("currentStatus") PackageStatus currentStatus,
            @Param("newStatus") PackageStatus newStatus,
            @Param("keptSource") EntitlementSource keptSource,
            @Param("updatedAt") Instant updatedAt
    );

    boolean existsByPurchaseIdAndSource(Long purchaseId, EntitlementSource source);

    @Modifying
    @Query("""
            update UserEntitlementEntity e
            set e.status = :newStatus, e.updatedAt = :updatedAt
            where e.purchaseId = :purchaseId
              and e.source = :source
              and e.status = :currentStatus
            """)
    int changeStatusByPurchaseId(
            @Param("purchaseId") Long purchaseId,
            @Param("source") EntitlementSource source,
            @Param("currentStatus") PackageStatus currentStatus,
            @Param("newStatus") PackageStatus newStatus,
            @Param("updatedAt") Instant updatedAt
    );
}
