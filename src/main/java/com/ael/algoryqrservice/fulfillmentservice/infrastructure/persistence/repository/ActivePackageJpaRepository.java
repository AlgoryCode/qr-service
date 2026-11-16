package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.ActivePackageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActivePackageJpaRepository extends JpaRepository<ActivePackageEntity, Long> {

    Optional<ActivePackageEntity> findByMerchantId(Long merchantId);

    @Query("""
            select a from ActivePackageEntity a
            where (a.status = :active
                    and (a.kind is null or a.kind <> :free)
                    and a.periodEnd is not null and a.periodEnd < :today)
               or (a.status = :grace and a.graceEndsAt is not null and a.graceEndsAt < :now)
            order by a.updatedAt asc
            """)
    List<ActivePackageEntity> findLifecycleDue(
            @Param("active") PackageStatus active,
            @Param("grace") PackageStatus grace,
            @Param("free") SubscriptionKind free,
            @Param("today") LocalDate today,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select a from ActivePackageEntity a
            where a.kind = :trial and a.status = :active
              and a.periodEnd between :fromDate and :toDate
            """)
    List<ActivePackageEntity> findTrialsEndingBetween(
            @Param("trial") SubscriptionKind trial,
            @Param("active") PackageStatus active,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
