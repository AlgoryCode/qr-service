package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PasswordChangeCode;
import com.ael.algoryqrservice.model.enums.PasswordChangeCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordChangeCodeRepository extends JpaRepository<PasswordChangeCode, Long> {

    Optional<PasswordChangeCode> findFirstByUserIdAndCodeAndStatusAndRevokedFalseOrderByCreatedAtDesc(
            Long userId,
            String code,
            PasswordChangeCodeStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordChangeCode c
            set c.revoked = true,
                c.revokedAt = :now
            where c.userId = :userId
              and c.status = com.ael.algoryqrservice.model.enums.PasswordChangeCodeStatus.PENDING
              and c.revoked = false
            """)
    int revokePendingByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordChangeCode c
            set c.status = com.ael.algoryqrservice.model.enums.PasswordChangeCodeStatus.EXPIRED
            where c.status = com.ael.algoryqrservice.model.enums.PasswordChangeCodeStatus.PENDING
              and c.revoked = false
              and c.expiresAt <= :now
            """)
    int expireAllTimedOut(@Param("now") LocalDateTime now);
}
