package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PlanPackageItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanPackageItemRepository extends JpaRepository<PlanPackageItem, Long> {

    List<PlanPackageItem> findByPlanPackageId(Long packageId);

    List<PlanPackageItem> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    void deleteByProductId(Long productId);

    long countByProductId(Long productId);
}
