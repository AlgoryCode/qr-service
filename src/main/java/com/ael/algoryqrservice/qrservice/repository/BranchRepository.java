package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByMerchantIdAndDeletedFalseOrderByIdDesc(Long merchantId);

    Optional<Branch> findByIdAndMerchantIdAndDeletedFalse(Long id, Long merchantId);

    long countByMerchantIdAndDeletedFalse(Long merchantId);

    long countByMerchantIdAndGrandfatheredTrueAndDeletedFalse(Long merchantId);

    List<Branch> findByMerchantIdAndDeletedFalse(Long merchantId);
}
