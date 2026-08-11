package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MainCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MainCategoryRepository extends JpaRepository<MainCategory, Long> {
    List<MainCategory> findByDeletedFalseOrderBySortOrderAscIdAsc();

    Optional<MainCategory> findByIdAndDeletedFalse(Long id);

    Optional<MainCategory> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlugAndDeletedFalse(String slug);

    @Query("""
            SELECT m FROM MainCategory m
            WHERE m.deleted = false
              AND (
                :qBlank = true
                OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(m.slug) LIKE LOWER(CONCAT('%', :q, '%'))
                OR EXISTS (
                    SELECT 1 FROM SubCategory s
                    WHERE s.mainCategoryId = m.id
                      AND s.deleted = false
                      AND (
                        LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))
                        OR LOWER(s.slug) LIKE LOWER(CONCAT('%', :q, '%'))
                      )
                )
              )
            """)
    Page<MainCategory> searchByNameOrSlugOrSub(
            @Param("q") String q,
            @Param("qBlank") boolean qBlank,
            Pageable pageable
    );
}
