package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.PurchaseReminder;
import com.ael.algoryqrservice.model.enums.PurchaseReminderType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseReminderRepository extends JpaRepository<PurchaseReminder, Long> {

    boolean existsByPurchaseIdAndReminderType(Long purchaseId, PurchaseReminderType reminderType);
}
