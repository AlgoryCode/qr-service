package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProductPairing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MenuProductPairingRepository extends JpaRepository<MenuProductPairing, Long> {

    List<MenuProductPairing> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    List<MenuProductPairing> findByProductIdInOrderBySortOrderAscIdAsc(Collection<Long> productIds);

    void deleteByProductId(Long productId);
}
