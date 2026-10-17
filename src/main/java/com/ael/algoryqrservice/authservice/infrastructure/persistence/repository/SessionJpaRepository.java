package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionJpaRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByIdAndRevokedFalse(UUID id);

    List<SessionEntity> findBySubjectTypeAndSubjectIdAndRevokedFalse(String subjectType, Long subjectId);
}
