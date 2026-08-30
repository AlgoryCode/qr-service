package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuSubCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuSubCategoryRepository extends JpaRepository<MenuSubCategory, Long> {

    List<MenuSubCategory> findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long menuId);

    List<MenuSubCategory> findByMenuCategoryIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long menuCategoryId);

    Optional<MenuSubCategory> findByIdAndDeletedFalse(Long id);

    Optional<MenuSubCategory> findByIdAndMenuIdAndDeletedFalse(Long id, Long menuId);

    Optional<MenuSubCategory> findByMenuIdAndSlugAndDeletedFalse(Long menuId, String slug);

    boolean existsByMenuIdAndSlugAndDeletedFalse(Long menuId, String slug);

    long countByMenuCategoryIdAndDeletedFalse(Long menuCategoryId);

    List<MenuSubCategory> findByIdInAndDeletedFalse(Collection<Long> ids);
}
