package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.RestaurantLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantLayoutRepository extends JpaRepository<RestaurantLayout, Long> {

    List<RestaurantLayout> findByUserIdOrderByIdAsc(Long userId);

    Optional<RestaurantLayout> findByIdAndUserId(Long id, Long userId);

    List<RestaurantLayout> findByUserIdAndBranchId(Long userId, Long branchId);

    List<RestaurantLayout> findByBranchId(Long branchId);
}
