package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "package_fulfillment")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PackageFulfillmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 128)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "package_code", length = 128)
    private String packageCode;

    @Column(name = "conversation_id", length = 256)
    private String conversationId;

    @Column(name = "payment_id", length = 128)
    private String paymentId;

    @Column(name = "subscription_id", length = 128)
    private String subscriptionId;

    @Column(name = "billing_cycle_number")
    private Integer billingCycleNumber;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FulfillmentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
