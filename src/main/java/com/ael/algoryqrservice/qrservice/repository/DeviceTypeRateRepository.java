package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceTypeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceTypeRateRepository extends JpaRepository<DeviceTypeRate, Long> {

    List<DeviceTypeRate> findByBranchIdAndMerchantId(Long branchId, Long merchantId);
}
