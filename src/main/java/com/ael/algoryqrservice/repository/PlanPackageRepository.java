package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PlanPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanPackageRepository extends JpaRepository<PlanPackage, Long> {

    Optional<PlanPackage> findByCode(String code);

    boolean existsByCode(String code);

    List<PlanPackage> findByActiveTrueOrderByPriceAsc();

    Optional<PlanPackage> findFirstByTrialEligibleTrueAndActiveTrueOrderByPriorityDesc();

    @Query("""
            select distinct pkg
            from PlanPackage pkg
            left join fetch pkg.items item
            left join fetch item.product
            where pkg.id = :id
            """)
    Optional<PlanPackage> findByIdWithItems(@Param("id") Long id);
}
