package com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.qrservice.model.enums.StaffRole;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantStaffRepository extends JpaRepository<MerchantStaff, Long> {

    Optional<MerchantStaff> findByUsernameIgnoreCase(String username);

    Optional<MerchantStaff> findByUsernameIgnoreCaseAndActiveTrue(String username);

    Optional<MerchantStaff> findByMerchantIdAndUsernameIgnoreCaseAndActiveTrue(Long merchantId, String username);

    @Query("select s from MerchantStaff s where s.id = :id and s.active = true")
    Optional<MerchantStaff> findActiveByIdWithRole(@Param("id") Long id);

    Optional<MerchantStaff> findByMerchantIdAndUsernameIgnoreCase(Long merchantId, String username);

    boolean existsByMerchantIdAndUsernameIgnoreCase(Long merchantId, String username);

    List<MerchantStaff> findByMerchantIdOrderByDisplayNameAsc(Long merchantId);

    Optional<MerchantStaff> findByIdAndMerchantId(Long id, Long merchantId);

    List<MerchantStaff> findByBranchIdOrderByDisplayNameAsc(Long branchId);

    List<MerchantStaff> findByBranchIdInOrderByDisplayNameAsc(Collection<Long> branchIds);

    Optional<MerchantStaff> findByIdAndBranchId(Long id, Long branchId);

    List<MerchantStaff> findByMerchantIdAndStaffRoleAndActiveTrueOrderByDisplayNameAsc(Long merchantId, StaffRole staffRole);

    boolean existsByUsernameIgnoreCase(String username);
}
