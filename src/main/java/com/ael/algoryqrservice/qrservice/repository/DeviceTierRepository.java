package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceTier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTierRepository extends JpaRepository<DeviceTier, Long> {

    List<DeviceTier> findByMerchantIdAndDeletedFalseOrderByMinSpentAscIdAsc(Long merchantId);

    List<DeviceTier> findByMerchantId(Long merchantId);
}
