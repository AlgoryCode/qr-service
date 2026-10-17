package com.ael.algoryqrservice.qrservice.product.repository;

import com.ael.algoryqrservice.qrservice.product.model.MenuProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MenuProductOptionRepository extends JpaRepository<MenuProductOption, Long> {

    @Query("""
            select o from MenuProductOption o
            join fetch o.group g
            where o.id in :ids
            """)
    List<MenuProductOption> findAllByIdInWithGroup(@Param("ids") Collection<Long> ids);
}
