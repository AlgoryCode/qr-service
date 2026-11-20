package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceMembershipPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceMembershipPlanRepository extends JpaRepository<DeviceMembershipPlan, Long> {

    List<DeviceMembershipPlan> findByMerchantIdAndDeletedFalseOrderByPriceAscIdAsc(Long merchantId);

    Optional<DeviceMembershipPlan> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);
}
