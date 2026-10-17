package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_subscription")
public class SubscriptionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "service_name", nullable = false, length = 64)
    private String serviceName;

    @Column(name = "source_reference_id", length = 64)
    private String sourceReferenceId;

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    @Column(name = "initial_conversation_id", nullable = false, unique = true, length = 64)
    private String initialConversationId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "billing_interval_months", nullable = false)
    private int billingIntervalMonths;

    @Column(name = "total_cycle_count")
    private Integer totalCycleCount;

    @Column(name = "completed_cycle_count", nullable = false)
    private int completedCycleCount;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "next_charge_at")
    private LocalDateTime nextChargeAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "request_payload", nullable = false, length = 16000)
    private String requestPayload;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
