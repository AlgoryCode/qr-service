package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentProvider;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_payment")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, unique = true, length = 64)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private PaymentProvider provider;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "payment_transaction_id", length = 64)
    private String paymentTransactionId;

    @Column(name = "gateway_token", length = 128)
    private String gatewayToken;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "basket_id", nullable = false, length = 64)
    private String basketId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "paid_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidPrice;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 32)
    private PaymentType paymentType;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "service_name", length = 64)
    private String serviceName;

    @Column(name = "source_reference_id", length = 64)
    private String sourceReferenceId;

    @Column(name = "source_metadata", length = 4000)
    private String sourceMetadata;

    @Column(name = "buyer_email", length = 255)
    private String buyerEmail;

    @Column(name = "buyer_name", length = 255)
    private String buyerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_style", nullable = false, length = 32)
    private PaymentStyle paymentStyle;

    @Column(name = "bank_installment_count")
    private Integer bankInstallmentCount;

    @Column(name = "subscription_id", length = 64)
    private String subscriptionId;

    @Column(name = "billing_cycle_number")
    private Integer billingCycleNumber;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "verification_only", nullable = false)
    private boolean verificationOnly;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (refundedAmount == null) {
            refundedAmount = BigDecimal.ZERO;
        }
        if (provider == null) {
            provider = PaymentProvider.PAYTR;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
