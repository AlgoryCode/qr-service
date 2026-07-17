package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.PurchaseReminderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tbl_purchase_reminder",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_reminder_purchase_type",
                columnNames = {"purchase_id", "reminder_type"}
        )
)
@Getter
@NoArgsConstructor
public class PurchaseReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 64)
    private PurchaseReminderType reminderType;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    public PurchaseReminder(Long purchaseId, PurchaseReminderType reminderType, UUID eventId) {
        this.purchaseId = purchaseId;
        this.reminderType = reminderType;
        this.eventId = eventId;
    }
}
