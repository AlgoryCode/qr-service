package com.ael.algoryqrservice.qrservice.integration.ubereats.repository;

import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UberEatsConnectionRepository extends JpaRepository<UberEatsConnection, Long> {

    Optional<UberEatsConnection> findByMerchantId(Long merchantId);

    List<UberEatsConnection> findByMerchantIdOrderByUpdatedAtDesc(Long merchantId);

    List<UberEatsConnection> findByStatus(UberEatsConnectionStatus status);

    List<UberEatsConnection> findByRestaurantId(String restaurantId);

    List<UberEatsConnection> findBySellerId(String sellerId);
}
