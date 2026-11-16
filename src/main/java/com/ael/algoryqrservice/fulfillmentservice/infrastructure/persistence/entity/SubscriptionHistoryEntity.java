package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "subscription_history", indexes = {
        @Index(name = "idx_subscription_history_merchant", columnList = "merchant_id, created_at")
})
@Getter
@Setter
public class SubscriptionHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private SubscriptionEventType eventType;

    @Column(name = "package_code", length = 128)
    private String packageCode;

    @Column(name = "previous_package_code", length = 128)
    private String previousPackageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    private PackageStatus status;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "dedupe_key", unique = true, length = 128)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
