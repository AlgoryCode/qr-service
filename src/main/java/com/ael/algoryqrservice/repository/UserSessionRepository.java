package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.UserSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdOrderByLoggedInAtDesc(Long userId);

    Page<UserSession> findByUserIdOrderByLoggedInAtDesc(Long userId, Pageable pageable);

    Optional<UserSession> findByIdAndUserId(UUID id, Long userId);
}
