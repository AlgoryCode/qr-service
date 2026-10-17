package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long menuId);

    List<MenuCategory> findByMerchantIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long merchantId);

    List<MenuCategory> findByMerchantIdAndMenuIdIsNullAndDeletedFalseOrderBySortOrderAscIdAsc(Long merchantId);

    Optional<MenuCategory> findByIdAndMenuIdAndDeletedFalse(Long id, Long menuId);

    Optional<MenuCategory> findByIdAndMerchantIdAndMenuIdIsNullAndDeletedFalse(Long id, Long merchantId);

    boolean existsByMerchantIdAndMenuIdIsNullAndSlugAndDeletedFalse(Long merchantId, String slug);

    Optional<MenuCategory> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    Optional<MenuCategory> findByMenuIdAndSlugAndDeletedFalse(Long menuId, String slug);

    boolean existsByMenuIdAndSlugAndDeletedFalse(Long menuId, String slug);

    long countByMenuIdAndDeletedFalse(Long menuId);
}
