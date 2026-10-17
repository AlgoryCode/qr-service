package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.EmailChangeChallenge;
import com.ael.algoryqrservice.qrservice.model.enums.EmailChangeChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmailChangeChallengeRepository extends JpaRepository<EmailChangeChallenge, UUID> {

    Optional<EmailChangeChallenge> findByIdAndMerchantId(UUID id, Long merchantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailChangeChallenge c
            set c.status = com.ael.algoryqrservice.qrservice.model.enums.EmailChangeChallengeStatus.CANCELLED
            where c.merchantId = :merchantId
              and c.status in :activeStatuses
            """)
    int cancelActiveByMerchantId(
            @Param("merchantId") Long merchantId,
            @Param("activeStatuses") java.util.Collection<EmailChangeChallengeStatus> activeStatuses
    );
}
