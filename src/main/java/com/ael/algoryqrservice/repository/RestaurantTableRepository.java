package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByMenuIdOrderByTableNumberAscNameAsc(Long menuId);

    Optional<RestaurantTable> findByPublicTokenAndActiveTrue(String publicToken);

    Optional<RestaurantTable> findByIdAndMenuId(Long id, Long menuId);

    Optional<RestaurantTable> findFirstByMenuIdAndNameIgnoreCaseAndActiveTrue(Long menuId, String name);

    Optional<RestaurantTable> findFirstByMenuIdAndActiveTrueOrderByTableNumberAscNameAsc(Long menuId);
}
