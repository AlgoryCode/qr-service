package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long menuId);

    Optional<MenuCategory> findByIdAndMenuIdAndDeletedFalse(Long id, Long menuId);

    Optional<MenuCategory> findByMenuIdAndSlugAndDeletedFalse(Long menuId, String slug);

    boolean existsByMenuIdAndSlugAndDeletedFalse(Long menuId, String slug);

    long countByMenuIdAndDeletedFalse(Long menuId);
}
