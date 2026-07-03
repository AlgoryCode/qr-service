package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdOrderByLoggedInAtDesc(Long userId);

    Optional<UserSession> findByIdAndUserId(UUID id, Long userId);
}
