package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.EmailChangeChallenge;
import com.ael.algoryqrservice.model.enums.EmailChangeChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmailChangeChallengeRepository extends JpaRepository<EmailChangeChallenge, UUID> {

    Optional<EmailChangeChallenge> findByIdAndUserId(UUID id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailChangeChallenge c
            set c.status = com.ael.algoryqrservice.model.enums.EmailChangeChallengeStatus.CANCELLED
            where c.userId = :userId
              and c.status in :activeStatuses
            """)
    int cancelActiveByUserId(
            @Param("userId") Long userId,
            @Param("activeStatuses") java.util.Collection<EmailChangeChallengeStatus> activeStatuses
    );
}
