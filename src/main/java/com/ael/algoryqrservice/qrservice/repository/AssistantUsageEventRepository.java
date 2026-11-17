package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.analytics.assistant.AssistantUsageOutcome;
import com.ael.algoryqrservice.qrservice.model.AssistantUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AssistantUsageEventRepository extends JpaRepository<AssistantUsageEvent, Long> {

    List<AssistantUsageEvent> findByMerchantIdAndBranchIdAndCreatedAtBetween(
            Long merchantId,
            Long branchId,
            LocalDateTime from,
            LocalDateTime to
    );

    long countByMerchantIdAndOutcomeAndCreatedAtGreaterThanEqual(
            Long merchantId,
            AssistantUsageOutcome outcome,
            LocalDateTime from
    );
}
