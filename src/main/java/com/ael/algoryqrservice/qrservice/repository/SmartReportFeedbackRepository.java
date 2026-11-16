package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.SmartReportFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SmartReportFeedbackRepository extends JpaRepository<SmartReportFeedback, Long> {

    Optional<SmartReportFeedback> findByProcessIdAndMerchantId(UUID processId, Long merchantId);

    /** Whether the merchant already earned the feedback bonus in the given week. */
    boolean existsByMerchantIdAndBonusWeek(Long merchantId, LocalDate bonusWeek);

    /** Whether that week's feedback bonus is earned and not yet spent. */
    boolean existsByMerchantIdAndBonusWeekAndBonusUsedAtIsNull(Long merchantId, LocalDate bonusWeek);

    /** Spends the week's bonus credit; returns 1 when it was available, 0 otherwise (safe under concurrency). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SmartReportFeedback f
               set f.bonusUsedAt = :usedAt
             where f.merchantId = :merchantId
               and f.bonusWeek = :bonusWeek
               and f.bonusUsedAt is null
            """)
    int consumeBonus(
            @Param("merchantId") Long merchantId,
            @Param("bonusWeek") LocalDate bonusWeek,
            @Param("usedAt") LocalDateTime usedAt
    );
}
