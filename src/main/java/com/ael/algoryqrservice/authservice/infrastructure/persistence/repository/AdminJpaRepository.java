package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.AdminEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminJpaRepository extends JpaRepository<AdminEntity, Long> {
    @EntityGraph(attributePaths = "role")
    Optional<AdminEntity> findByEmailIgnoreCaseAndActiveTrue(String email);

    @EntityGraph(attributePaths = "role")
    @Query("select a from AdminEntity a where a.id = :id and a.active = true")
    Optional<AdminEntity> findActiveByIdWithRole(@Param("id") Long id);
}
