package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProductOptionGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MenuProductOptionGroupRepository extends JpaRepository<MenuProductOptionGroup, Long> {

    @EntityGraph(attributePaths = "options")
    List<MenuProductOptionGroup> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    @EntityGraph(attributePaths = "options")
    List<MenuProductOptionGroup> findByProductIdInOrderBySortOrderAscIdAsc(Collection<Long> productIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MenuProductOptionGroup g where g.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
