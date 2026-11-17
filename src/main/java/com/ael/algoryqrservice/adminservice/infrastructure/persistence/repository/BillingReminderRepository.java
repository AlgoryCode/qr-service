package com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.BillingReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BillingReminderRepository extends JpaRepository<BillingReminder, Long> {

    boolean existsByPurchaseIdAndKindAndPeriodEndAndSentBy(Long purchaseId, BillingReminder.Kind kind,
                                                           LocalDateTime periodEnd, String sentBy);

    List<BillingReminder> findTop20ByMerchantIdOrderBySentAtDesc(Long merchantId);

    List<BillingReminder> findBySentAtAfter(LocalDateTime since);
}
