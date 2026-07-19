package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseCancellationReason;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_purchase", indexes = {
        @Index(name = "idx_purchase_user_id", columnList = "user_id"),
        @Index(name = "idx_purchase_status", columnList = "status"),
        @Index(name = "idx_purchase_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "package_code", nullable = false, length = 64)
    private String packageCode;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status;

    @Column(name = "payment_conversation_id", length = 128)
    private String paymentConversationId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    @Column(name = "card_brand", length = 64)
    private String cardBrand;

    @Column(name = "card_last_four", length = 8)
    private String cardLastFour;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 24)
    @ColumnDefault("'THREE_DS'")
    @Builder.Default
    private PaymentMode paymentMode = PaymentMode.THREE_DS;

    @Column(name = "installment_count", nullable = false)
    @ColumnDefault("1")
    @Builder.Default
    private Integer installmentCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = true, length = 16)
    @Builder.Default
    private PurchaseType purchaseType = PurchaseType.PAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_style", nullable = true, length = 24)
    @Builder.Default
    private PaymentStyle paymentStyle = PaymentStyle.ONE_TIME;

    @Column(name = "subscription_id", length = 128)
    private String subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 24)
    private SubscriptionStatus subscriptionStatus;

    @Embedded
    private BillingSnapshot billingSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 32)
    private PurchaseCancellationReason cancellationReason;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "purchased_at", nullable = false, updatable = false)
    private LocalDateTime purchasedAt;

    public boolean isStartedByDate() {
        return startsAt == null || !startsAt.isAfter(LocalDateTime.now());
    }

    public boolean isExpiredByDate() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsable() {
        return status == PurchaseStatus.ACTIVE
                && expiresAt != null
                && isStartedByDate()
                && !isExpiredByDate();
    }

    public boolean isEffectivelyExpired() {
        return status == PurchaseStatus.EXPIRED
                || status == PurchaseStatus.CANCELLED
                || isExpiredByDate();
    }
}
