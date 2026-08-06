package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MainCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MainCategoryRepository extends JpaRepository<MainCategory, Long> {
    List<MainCategory> findByDeletedFalseOrderBySortOrderAscIdAsc();

    Optional<MainCategory> findByIdAndDeletedFalse(Long id);

    Optional<MainCategory> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlugAndDeletedFalse(String slug);
}
