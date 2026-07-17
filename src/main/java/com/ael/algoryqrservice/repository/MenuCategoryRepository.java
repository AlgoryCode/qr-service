package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
