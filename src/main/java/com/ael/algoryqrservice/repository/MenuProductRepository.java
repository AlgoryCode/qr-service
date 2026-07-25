package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuProductRepository extends JpaRepository<MenuProduct, Long> {
    List<MenuProduct> findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(Long menuId);

    Page<MenuProduct> findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(Long menuId, Pageable pageable);

    Page<MenuProduct> findByMenuIdAndDeletedFalseAndAvailableTrueOrderBySortOrderAscProductIdAsc(
            Long menuId,
            Pageable pageable
    );

    Optional<MenuProduct> findByProductIdAndDeletedFalse(Long productId);

    long countByCategoryIdAndDeletedFalse(Long categoryId);

    @Query("""
            select menu, product
            from Menu menu
            left join MenuProduct product
              on product.menuId = menu.menuId and product.deleted = false
            where menu.qrId = :qrId
              and menu.userId = :userId
              and menu.active = true
              and menu.deleted = false
            order by product.sortOrder asc, product.productId asc
            """)
    List<Object[]> findMenuWithProductsByQrIdAndUserId(
            @Param("qrId") Long qrId,
            @Param("userId") Long userId
    );
}
