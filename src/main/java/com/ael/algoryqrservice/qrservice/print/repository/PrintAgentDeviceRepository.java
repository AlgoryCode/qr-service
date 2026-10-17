package com.ael.algoryqrservice.qrservice.print.repository;

import com.ael.algoryqrservice.qrservice.print.model.PrintAgentDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrintAgentDeviceRepository extends JpaRepository<PrintAgentDevice, Long> {

    Optional<PrintAgentDevice> findByDeviceTokenHashAndEnabledTrue(String deviceTokenHash);

    List<PrintAgentDevice> findByMerchantIdAndBranchIdOrderByIdDesc(Long merchantId, Long branchId);

    Optional<PrintAgentDevice> findByIdAndMerchantId(Long id, Long merchantId);

    List<PrintAgentDevice> findByMerchantIdAndEnabledTrueOrderByLastSeenAtDescIdDesc(Long merchantId);

    boolean existsByBranchIdAndEnabledTrue(Long branchId);
}
