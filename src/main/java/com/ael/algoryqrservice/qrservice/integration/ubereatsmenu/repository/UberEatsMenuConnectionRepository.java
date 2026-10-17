package com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.repository;

import com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.model.UberEatsMenuConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UberEatsMenuConnectionRepository extends JpaRepository<UberEatsMenuConnection, Long> {

    List<UberEatsMenuConnection> findByMerchantIdOrderByUpdatedAtDesc(Long merchantId);

    Optional<UberEatsMenuConnection> findByMerchantIdAndMenuId(Long merchantId, Long menuId);

    Optional<UberEatsMenuConnection> findByMenuId(Long menuId);
}
