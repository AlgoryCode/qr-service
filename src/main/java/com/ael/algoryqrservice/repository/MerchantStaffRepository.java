package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MerchantStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantStaffRepository extends JpaRepository<MerchantStaff, Long> {

    Optional<MerchantStaff> findByUsernameIgnoreCase(String username);

    List<MerchantStaff> findByBranchIdOrderByDisplayNameAsc(Long branchId);

    List<MerchantStaff> findByBranchIdInOrderByDisplayNameAsc(Collection<Long> branchIds);

    Optional<MerchantStaff> findByIdAndBranchId(Long id, Long branchId);

    boolean existsByUsernameIgnoreCase(String username);
}
