package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.ConsoleRateSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsoleRateSettingRepository extends JpaRepository<ConsoleRateSetting, Long> {

    Optional<ConsoleRateSetting> findByBranchIdAndMerchantId(Long branchId, Long merchantId);
}
