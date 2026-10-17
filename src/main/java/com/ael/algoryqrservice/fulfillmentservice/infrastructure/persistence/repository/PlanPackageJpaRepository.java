package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.PlanPackageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlanPackageJpaRepository extends JpaRepository<PlanPackageEntity, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select p from PlanPackageEntity p where p.code = :code")
    Optional<PlanPackageEntity> findByCodeWithItems(@Param("code") String code);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select p from PlanPackageEntity p where p.id = :id")
    Optional<PlanPackageEntity> findByIdWithItems(@Param("id") Long id);

    Page<PlanPackageEntity> findByActiveTrue(Pageable pageable);

    Page<PlanPackageEntity> findByActiveTrueAndPurchasableTrue(Pageable pageable);
}
