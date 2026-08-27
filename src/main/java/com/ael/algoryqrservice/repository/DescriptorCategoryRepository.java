package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.DescriptorCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DescriptorCategoryRepository extends JpaRepository<DescriptorCategory, Long> {

    List<DescriptorCategory> findByDeletedFalseOrderBySortOrderAscIdAsc();

    List<DescriptorCategory> findBySubCategoryIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long subCategoryId);

    Optional<DescriptorCategory> findByIdAndDeletedFalse(Long id);

    Optional<DescriptorCategory> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlugAndDeletedFalse(String slug);

    long countBySubCategoryIdAndDeletedFalse(Long subCategoryId);
}
