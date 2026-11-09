package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.SessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionJpaRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByIdAndRevokedFalse(UUID id);

    /** Refresh takes the row lock, so two refreshes of one session run one after the other, never both rotating. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionEntity s where s.id = :id")
    Optional<SessionEntity> findByIdForUpdate(@Param("id") UUID id);

    List<SessionEntity> findBySubjectTypeAndSubjectIdAndRevokedFalse(String subjectType, Long subjectId);
}
