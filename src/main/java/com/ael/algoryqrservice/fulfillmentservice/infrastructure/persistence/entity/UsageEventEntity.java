package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Read-only mapping of {@code usage_events} (written by {@code MeteringService} in SQL). Mapped so
 * the schema exists wherever Hibernate creates it, e.g. in tests.
 */
@Entity
@Table(name = "usage_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_usage_events_idempotency", columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor
public class UsageEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(length = 128)
    private String reference;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
