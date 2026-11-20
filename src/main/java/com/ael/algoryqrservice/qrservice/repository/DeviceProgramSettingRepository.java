package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceProgramSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceProgramSettingRepository extends JpaRepository<DeviceProgramSetting, Long> {

    Optional<DeviceProgramSetting> findByMerchantId(Long merchantId);
}
