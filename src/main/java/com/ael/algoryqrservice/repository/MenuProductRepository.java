package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuProductRepository extends JpaRepository<MenuProduct, Long> {
    List<MenuProduct> findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(Long menuId);

    Optional<MenuProduct> findByProductIdAndDeletedFalse(Long productId);

    long countByCategoryIdAndDeletedFalse(Long categoryId);
}
