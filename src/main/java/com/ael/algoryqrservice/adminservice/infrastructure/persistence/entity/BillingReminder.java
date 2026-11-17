package com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A payment reminder e-mail sent to a member. */
@Entity
@Table(name = "billing_reminder")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingReminder {

    public static final String SYSTEM = "system";

    public enum Kind {
        /** A package that does not renew by itself ends in 7 days. */
        DUE_IN_7,
        /** ... ends tomorrow. */
        DUE_IN_1,
        /** The renewal charge failed a week ago and is still unpaid (the failure itself already e-mails). */
        OVERDUE_7,
        /** Sent by an admin from the panel. */
        MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Kind kind;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    private String email;

    @Column(name = "sent_by", nullable = false, length = 128)
    private String sentBy;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
