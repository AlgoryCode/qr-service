package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.DeviceShift;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceShiftRepository extends JpaRepository<DeviceShift, Long> {

    Optional<DeviceShift> findFirstByBranchIdAndMerchantIdAndStaffIdIsNullAndClosedAtIsNullAndDeletedFalse(
            Long branchId,
            Long merchantId
    );

    Optional<DeviceShift> findFirstByBranchIdAndMerchantIdAndStaffIdAndClosedAtIsNullAndDeletedFalse(
            Long branchId,
            Long merchantId,
            Long staffId
    );

    /** The open shift of a staff member, or of the owner when {@code staffId} is null. */
    default Optional<DeviceShift> findOpen(Long branchId, Long merchantId, Long staffId) {
        return staffId == null
                ? findFirstByBranchIdAndMerchantIdAndStaffIdIsNullAndClosedAtIsNullAndDeletedFalse(branchId, merchantId)
                : findFirstByBranchIdAndMerchantIdAndStaffIdAndClosedAtIsNullAndDeletedFalse(branchId, merchantId, staffId);
    }

    Optional<DeviceShift> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    org.springframework.data.domain.Page<DeviceShift> findByBranchIdAndMerchantIdAndDeletedFalseOrderByOpenedAtDescIdDesc(
            Long branchId,
            Long merchantId,
            org.springframework.data.domain.Pageable pageable
    );
}
