package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceTariffRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTariffRuleRepository extends JpaRepository<DeviceTariffRule, Long> {

    List<DeviceTariffRule> findByBranchIdAndMerchantIdAndDeletedFalseOrderByIdAsc(Long branchId, Long merchantId);

    Optional<DeviceTariffRule> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);
}
