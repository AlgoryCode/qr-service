package com.ael.algoryqrservice.integration.ubereats.repository;

import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UberEatsConnectionRepository extends JpaRepository<UberEatsConnection, Long> {

    Optional<UberEatsConnection> findByUserId(Long userId);

    List<UberEatsConnection> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<UberEatsConnection> findByStatus(UberEatsConnectionStatus status);

    List<UberEatsConnection> findByRestaurantId(String restaurantId);

    List<UberEatsConnection> findBySellerId(String sellerId);
}
