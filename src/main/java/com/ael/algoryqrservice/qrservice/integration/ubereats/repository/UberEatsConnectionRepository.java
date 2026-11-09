package com.ael.algoryqrservice.qrservice.integration.ubereats.repository;

import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UberEatsConnectionRepository extends JpaRepository<UberEatsConnection, Long> {

    /** A merchant has one connection per branch, plus possibly one without a branch (see V168). */
    Optional<UberEatsConnection> findByMerchantIdAndBranchId(Long merchantId, Long branchId);

    List<UberEatsConnection> findByMerchantIdOrderByUpdatedAtDesc(Long merchantId);

    List<UberEatsConnection> findByStatus(UberEatsConnectionStatus status);

    List<UberEatsConnection> findByRestaurantId(String restaurantId);

    List<UberEatsConnection> findBySellerId(String sellerId);
}
