package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.qrservice.model.MerchantStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MerchantStaffJpaRepository extends JpaRepository<MerchantStaff, Long> {

    Optional<MerchantStaff> findByUsernameIgnoreCase(String username);

    Optional<MerchantStaff> findByUsernameIgnoreCaseAndActiveTrue(String username);

    Optional<MerchantStaff> findByMerchantIdAndUsernameIgnoreCaseAndActiveTrue(Long merchantId, String username);

    @Query("select s from MerchantStaff s where s.id = :id and s.active = true")
    Optional<MerchantStaff> findActiveByIdWithRole(@Param("id") Long id);
}
