package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceMaintenanceLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceMaintenanceLogRepository extends JpaRepository<DeviceMaintenanceLog, Long> {

    List<DeviceMaintenanceLog> findByConsoleIdAndDeletedFalseOrderByStartedAtDescIdDesc(Long consoleId);

    List<DeviceMaintenanceLog> findByBranchIdAndMerchantIdAndDeletedFalse(Long branchId, Long merchantId);

    Optional<DeviceMaintenanceLog> findFirstByConsoleIdAndEndedAtIsNullAndDeletedFalseOrderByStartedAtDesc(Long consoleId);
}
