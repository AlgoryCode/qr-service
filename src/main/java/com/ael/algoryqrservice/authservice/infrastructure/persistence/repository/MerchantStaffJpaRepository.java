package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantStaffEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MerchantStaffJpaRepository extends JpaRepository<MerchantStaffEntity, Long> {
    @EntityGraph(attributePaths = "role")
    Optional<MerchantStaffEntity> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = "role")
    Optional<MerchantStaffEntity> findByUsernameIgnoreCaseAndActiveTrue(String username);

    @EntityGraph(attributePaths = "role")
    Optional<MerchantStaffEntity> findByMerchantIdAndUsernameIgnoreCaseAndActiveTrue(Long merchantId, String username);

    @EntityGraph(attributePaths = "role")
    @Query("select s from MerchantStaffEntity s where s.id = :id and s.active = true")
    Optional<MerchantStaffEntity> findActiveByIdWithRole(@Param("id") Long id);
}
