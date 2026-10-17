package com.ael.algoryqrservice.qrservice.product.repository;

import com.ael.algoryqrservice.qrservice.product.model.MenuProductOptionGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuProductOptionGroupRepository extends JpaRepository<MenuProductOptionGroup, Long> {

    @EntityGraph(attributePaths = "options")
    List<MenuProductOptionGroup> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    @EntityGraph(attributePaths = "options")
    List<MenuProductOptionGroup> findByProductIdInOrderBySortOrderAscIdAsc(Collection<Long> productIds);

    @EntityGraph(attributePaths = "options")
    List<MenuProductOptionGroup> findByMerchantIdAndProductIdIsNullOrderBySortOrderAscIdAsc(Long merchantId);

    @EntityGraph(attributePaths = "options")
    Optional<MenuProductOptionGroup> findByIdAndMerchantIdAndProductIdIsNull(Long id, Long merchantId);

    List<MenuProductOptionGroup> findByLibraryGroupIdIn(Collection<Long> libraryGroupIds);

    List<MenuProductOptionGroup> findByLibraryGroupIdAndProductIdIn(Long libraryGroupId, Collection<Long> productIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MenuProductOptionGroup g where g.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
