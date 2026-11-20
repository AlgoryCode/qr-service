package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DevicePackage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevicePackageRepository extends JpaRepository<DevicePackage, Long> {

    List<DevicePackage> findByBranchIdAndMerchantIdAndDeletedFalseOrderByPriceAscIdAsc(Long branchId, Long merchantId);

    Optional<DevicePackage> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);
}
