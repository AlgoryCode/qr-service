package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MerchantJpaRepository extends JpaRepository<MerchantEntity, Long> {
    @EntityGraph(attributePaths = "role")
    Optional<MerchantEntity> findByEmailIgnoreCaseAndActiveTrueAndDeletedAtIsNull(String email);

    @EntityGraph(attributePaths = "role")
    @Query("select m from MerchantEntity m where m.id = :id and m.active = true and m.deletedAt is null")
    Optional<MerchantEntity> findActiveByIdWithRole(@Param("id") Long id);

    @EntityGraph(attributePaths = "role")
    @Query("select m from MerchantEntity m where m.id = :id")
    Optional<MerchantEntity> findWithRoleById(@Param("id") Long id);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    @EntityGraph(attributePaths = "role")
    Optional<MerchantEntity> findByProviderSubjectAndDeletedAtIsNull(String providerSubject);

    @EntityGraph(attributePaths = "role")
    Optional<MerchantEntity> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
