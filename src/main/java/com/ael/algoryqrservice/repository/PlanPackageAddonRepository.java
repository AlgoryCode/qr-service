package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PlanPackageAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanPackageAddonRepository extends JpaRepository<PlanPackageAddon, Long> {

    @Query("SELECT a FROM PlanPackageAddon a WHERE a.planPackage.id = :packageId AND a.active = true")
    List<PlanPackageAddon> findActiveByPackageId(@Param("packageId") Long packageId);

    @Query("SELECT a FROM PlanPackageAddon a WHERE a.planPackage.id = :packageId AND a.product.id = :productId")
    Optional<PlanPackageAddon> findByPackageIdAndProductId(
            @Param("packageId") Long packageId,
            @Param("productId") Long productId
    );
}
