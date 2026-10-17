package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlanPackageJpaRepository extends JpaRepository<PlanPackage, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select p from PlanPackage p where p.code = :code")
    Optional<PlanPackage> findByCodeWithItems(@Param("code") String code);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select p from PlanPackage p where p.id = :id")
    Optional<PlanPackage> findByIdWithItems(@Param("id") Long id);

    Page<PlanPackage> findByActiveTrue(Pageable pageable);

    Page<PlanPackage> findByActiveTrueAndPurchasableTrue(Pageable pageable);
}
