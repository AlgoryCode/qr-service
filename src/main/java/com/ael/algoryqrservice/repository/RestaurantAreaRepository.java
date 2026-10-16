package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.RestaurantArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantAreaRepository extends JpaRepository<RestaurantArea, Long> {

    List<RestaurantArea> findByMenuIdOrderBySortOrderAscIdAsc(Long menuId);

    List<RestaurantArea> findByMenuIdInOrderBySortOrderAscIdAsc(Collection<Long> menuIds);

    Optional<RestaurantArea> findByIdAndMenuId(Long id, Long menuId);

    boolean existsByMenuIdAndNameIgnoreCase(Long menuId, String name);

    List<RestaurantArea> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    Optional<RestaurantArea> findByIdAndUserId(Long id, Long userId);

    List<RestaurantArea> findByUserIdAndMenuIdIsNullOrderBySortOrderAscIdAsc(Long userId);

    List<RestaurantArea> findByLayoutIdOrderBySortOrderAscIdAsc(Long layoutId);

    List<RestaurantArea> findByLayoutIdInOrderBySortOrderAscIdAsc(Collection<Long> layoutIds);
}
