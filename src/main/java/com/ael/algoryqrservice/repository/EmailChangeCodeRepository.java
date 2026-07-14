package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.EmailChangeCode;
import com.ael.algoryqrservice.model.enums.EmailChangeCodePurpose;
import com.ael.algoryqrservice.model.enums.EmailChangeCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailChangeCodeRepository extends JpaRepository<EmailChangeCode, Long> {

    Optional<EmailChangeCode> findFirstByChallengeIdAndPurposeAndCodeAndStatusAndRevokedFalseOrderByCreatedAtDesc(
            UUID challengeId,
            EmailChangeCodePurpose purpose,
            String code,
            EmailChangeCodeStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailChangeCode c
            set c.revoked = true,
                c.revokedAt = :now
            where c.userId = :userId
              and c.status = com.ael.algoryqrservice.model.enums.EmailChangeCodeStatus.PENDING
              and c.revoked = false
            """)
    int revokePendingByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailChangeCode c
            set c.revoked = true,
                c.revokedAt = :now
            where c.challengeId = :challengeId
              and c.purpose = :purpose
              and c.status = com.ael.algoryqrservice.model.enums.EmailChangeCodeStatus.PENDING
              and c.revoked = false
            """)
    int revokePendingByChallengeAndPurpose(
            @Param("challengeId") UUID challengeId,
            @Param("purpose") EmailChangeCodePurpose purpose,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailChangeCode c
            set c.status = com.ael.algoryqrservice.model.enums.EmailChangeCodeStatus.EXPIRED
            where c.status = com.ael.algoryqrservice.model.enums.EmailChangeCodeStatus.PENDING
              and c.revoked = false
              and c.expiresAt <= :now
            """)
    int expireAllTimedOut(@Param("now") LocalDateTime now);
}
