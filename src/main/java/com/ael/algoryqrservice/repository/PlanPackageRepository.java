package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.enums.PackageCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanPackageRepository extends JpaRepository<PlanPackage, Long> {

    Optional<PlanPackage> findByCode(PackageCode code);

    boolean existsByCode(PackageCode code);

    List<PlanPackage> findByActiveTrueOrderByPriceAsc();
}
