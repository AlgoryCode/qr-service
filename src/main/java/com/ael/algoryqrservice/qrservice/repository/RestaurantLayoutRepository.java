package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.RestaurantLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantLayoutRepository extends JpaRepository<RestaurantLayout, Long> {

    List<RestaurantLayout> findByMerchantIdOrderByIdAsc(Long merchantId);

    Optional<RestaurantLayout> findByIdAndUserId(Long id, Long merchantId);

    List<RestaurantLayout> findByMerchantIdAndBranchId(Long merchantId, Long branchId);

    List<RestaurantLayout> findByBranchId(Long branchId);
}
