package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findByMenuIdAndDeletedFalseOrderBySortOrderAscCategoryIdAsc(Long menuId);

    Optional<MenuCategory> findByCategoryIdAndDeletedFalse(Long categoryId);

    boolean existsByParentIdAndDeletedFalse(Long parentId);

    boolean existsByMenuIdAndParentIdAndNameIgnoreCaseAndDeletedFalse(Long menuId, Long parentId, String name);

    boolean existsByMenuIdAndParentIdAndNameIgnoreCaseAndDeletedFalseAndCategoryIdNot(
            Long menuId,
            Long parentId,
            String name,
            Long categoryId
    );

    long countByMenuIdAndParentIdAndDeletedFalse(Long menuId, Long parentId);

    @Query("""
            select menu, category
            from Menu menu
            left join MenuCategory category
              on category.menuId = menu.menuId and category.deleted = false
            where menu.qrId = :qrId
              and menu.userId = :userId
              and menu.active = true
              and menu.deleted = false
            order by category.sortOrder asc, category.categoryId asc
            """)
    List<Object[]> findMenuWithCategoriesByQrIdAndUserId(
            @Param("qrId") Long qrId,
            @Param("userId") Long userId
    );
}
