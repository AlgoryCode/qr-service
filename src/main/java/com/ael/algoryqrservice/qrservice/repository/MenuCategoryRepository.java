package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long menuId);

    List<MenuCategory> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    List<MenuCategory> findByUserIdAndMenuIdIsNullAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    Optional<MenuCategory> findByIdAndMenuIdAndDeletedFalse(Long id, Long menuId);

    Optional<MenuCategory> findByIdAndUserIdAndMenuIdIsNullAndDeletedFalse(Long id, Long userId);

    boolean existsByUserIdAndMenuIdIsNullAndSlugAndDeletedFalse(Long userId, String slug);

    Optional<MenuCategory> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    Optional<MenuCategory> findByMenuIdAndSlugAndDeletedFalse(Long menuId, String slug);

    boolean existsByMenuIdAndSlugAndDeletedFalse(Long menuId, String slug);

    long countByMenuIdAndDeletedFalse(Long menuId);
}
