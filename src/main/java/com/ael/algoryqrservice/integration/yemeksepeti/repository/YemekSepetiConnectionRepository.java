package com.ael.algoryqrservice.integration.yemeksepeti.repository;

import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiConnection;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YemekSepetiConnectionRepository extends JpaRepository<YemekSepetiConnection, Long> {

    Optional<YemekSepetiConnection> findByUserId(Long userId);

    List<YemekSepetiConnection> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<YemekSepetiConnection> findByStatus(YemekSepetiConnectionStatus status);

    List<YemekSepetiConnection> findByVendorId(String vendorId);

    List<YemekSepetiConnection> findByChainId(String chainId);
}
