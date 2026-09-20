package com.ael.algoryqrservice.print.repository;

import com.ael.algoryqrservice.print.model.PrintAgentDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrintAgentDeviceRepository extends JpaRepository<PrintAgentDevice, Long> {

    Optional<PrintAgentDevice> findByDeviceTokenHashAndEnabledTrue(String deviceTokenHash);

    List<PrintAgentDevice> findByOwnerUserIdAndBranchIdOrderByIdDesc(Long ownerUserId, Long branchId);

    Optional<PrintAgentDevice> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<PrintAgentDevice> findByOwnerUserIdAndEnabledTrueOrderByLastSeenAtDescIdDesc(Long ownerUserId);

    boolean existsByBranchIdAndEnabledTrue(Long branchId);
}
