package com.ael.algoryqrservice.integration.ubereats.repository;

import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UberEatsConnectionRepository extends JpaRepository<UberEatsConnection, Long> {

    List<UberEatsConnection> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<UberEatsConnection> findByUserIdAndMenuId(Long userId, Long menuId);

    Optional<UberEatsConnection> findByMenuId(Long menuId);
}
