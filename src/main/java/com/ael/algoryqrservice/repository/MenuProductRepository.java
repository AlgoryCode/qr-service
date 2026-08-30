package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuProductRepository extends JpaRepository<MenuProduct, Long>, JpaSpecificationExecutor<MenuProduct> {
    List<MenuProduct> findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(Long menuId);

    List<MenuProduct> findByMenuIdInAndDeletedFalseOrderBySortOrderAscProductIdAsc(Collection<Long> menuIds);

    Page<MenuProduct> findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(Long menuId, Pageable pageable);

    Page<MenuProduct> findByMenuIdAndDeletedFalseAndAvailableTrueOrderBySortOrderAscProductIdAsc(
            Long menuId,
            Pageable pageable
    );

    @Query(
            value = """
                    select p from MenuProduct p
                    where p.menuId = :menuId
                      and p.deleted = false
                      and (:availableOnly = false or p.available = true)
                      and (:chefRecommended is null or p.chefRecommended = :chefRecommended)
                      and (:minRating is null or p.ratingAvg >= :minRating)
                      and (:tagId is null or :tagId member of p.tagIds)
                      and (:subCategoryId is null or p.subCategoryId = :subCategoryId)
                    order by p.sortOrder asc, p.productId asc
                    """,
            countQuery = """
                    select count(p) from MenuProduct p
                    where p.menuId = :menuId
                      and p.deleted = false
                      and (:availableOnly = false or p.available = true)
                      and (:chefRecommended is null or p.chefRecommended = :chefRecommended)
                      and (:minRating is null or p.ratingAvg >= :minRating)
                      and (:tagId is null or :tagId member of p.tagIds)
                      and (:subCategoryId is null or p.subCategoryId = :subCategoryId)
                    """
    )
    Page<MenuProduct> searchProducts(
            @Param("menuId") Long menuId,
            @Param("availableOnly") boolean availableOnly,
            @Param("chefRecommended") Boolean chefRecommended,
            @Param("minRating") BigDecimal minRating,
            @Param("tagId") Long tagId,
            @Param("subCategoryId") Long subCategoryId,
            Pageable pageable
    );

    Optional<MenuProduct> findByProductIdAndDeletedFalse(Long productId);

    List<MenuProduct> findByProductIdInAndDeletedFalse(Collection<Long> productIds);

    long countBySubCategoryIdAndDeletedFalse(Long subCategoryId);

    long countByMenuIdAndDeletedFalse(Long menuId);

    @Query("""
            select count(p)
            from MenuProduct p
            join Menu m on p.menuId = m.menuId
            where m.userId = :userId
              and p.deleted = false
              and m.deleted = false
            """)
    long countActiveProductsForUser(@Param("userId") Long userId);

    boolean existsByMenuIdAndNameIgnoreCaseAndDeletedFalse(Long menuId, String name);

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
