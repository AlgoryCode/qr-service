package com.ael.algoryqrservice.qrservice.integration.yemeksepeti.repository;

import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.YemekSepetiConnection;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.YemekSepetiConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YemekSepetiConnectionRepository extends JpaRepository<YemekSepetiConnection, Long> {

    Optional<YemekSepetiConnection> findByMerchantId(Long merchantId);

    List<YemekSepetiConnection> findByMerchantIdOrderByUpdatedAtDesc(Long merchantId);

    List<YemekSepetiConnection> findByStatus(YemekSepetiConnectionStatus status);

    List<YemekSepetiConnection> findByVendorId(String vendorId);

    List<YemekSepetiConnection> findByChainId(String chainId);
}
