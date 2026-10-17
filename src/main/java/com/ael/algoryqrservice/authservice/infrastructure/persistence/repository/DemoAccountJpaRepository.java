package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.DemoAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DemoAccountJpaRepository extends JpaRepository<DemoAccountEntity, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    Optional<DemoAccountEntity> findByUsernameIgnoreCase(String username);

    Optional<DemoAccountEntity> findByUserId(Long userId);
}
