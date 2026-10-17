package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentStatus;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.PackageFulfillmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PackageFulfillmentJpaRepository extends JpaRepository<PackageFulfillmentEntity, Long> {

    Optional<PackageFulfillmentEntity> findByEventId(String eventId);

    Page<PackageFulfillmentEntity> findByMerchantIdOrderByCreatedAtDesc(Long merchantId, Pageable pageable);

    @Modifying
    @Query("""
            update PackageFulfillmentEntity f
            set f.status = :status, f.updatedAt = CURRENT_TIMESTAMP
            where f.merchantId = :merchantId and f.id <> :keepId and f.status = :activeStatus
            """)
    int supersedeOthers(
            @Param("merchantId") Long merchantId,
            @Param("keepId") Long keepId,
            @Param("status") FulfillmentStatus status,
            @Param("activeStatus") FulfillmentStatus activeStatus
    );
}
