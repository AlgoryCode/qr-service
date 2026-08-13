package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.TableSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TableSessionRepository extends JpaRepository<TableSession, UUID> {

    Optional<TableSession> findBySessionTokenAndRevokedFalse(String sessionToken);
}
