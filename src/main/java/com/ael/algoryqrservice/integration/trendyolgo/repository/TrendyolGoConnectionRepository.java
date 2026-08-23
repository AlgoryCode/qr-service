package com.ael.algoryqrservice.integration.trendyolgo.repository;

import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoConnection;
import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrendyolGoConnectionRepository extends JpaRepository<TrendyolGoConnection, Long> {

    Optional<TrendyolGoConnection> findByUserIdAndBranchId(Long userId, Long branchId);

    List<TrendyolGoConnection> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<TrendyolGoConnection> findByStatus(TrendyolGoConnectionStatus status);

    List<TrendyolGoConnection> findByRestaurantId(String restaurantId);

    List<TrendyolGoConnection> findBySellerId(String sellerId);
}
