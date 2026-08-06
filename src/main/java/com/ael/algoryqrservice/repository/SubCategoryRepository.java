package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.SubCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubCategoryRepository extends JpaRepository<SubCategory, Long> {
    List<SubCategory> findByDeletedFalseOrderBySortOrderAscIdAsc();

    List<SubCategory> findByMainCategoryIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long mainCategoryId);

    Optional<SubCategory> findByIdAndDeletedFalse(Long id);

    Optional<SubCategory> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlugAndDeletedFalse(String slug);

    Optional<SubCategory> findFirstByNameIgnoreCaseAndDeletedFalse(String name);
}
