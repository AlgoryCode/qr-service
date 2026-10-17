package com.ael.algoryqrservice.qrservice.model.dto;

import com.ael.algoryqrservice.qrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.qrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.qrservice.model.BillingSnapshot;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseResponse {

    private Long id;
    private Long userId;
    private Long packageId;
    private String packageCode;
    private String packageName;
    private BigDecimal price;
    private String currency;
    private PurchaseStatus status;
    private PaymentMode paymentMode;
    private PaymentStyle paymentStyle;
    private PurchaseType purchaseType;
    private Integer installmentCount;
    private String paymentId;
    private String paymentConversationId;
    private String currentPeriodConversationId;
    private Long paymentMethodId;
    private String cardBrand;
    private String cardLastFour;
    private String subscriptionId;
    private SubscriptionStatus subscriptionStatus;
    private BillingPeriod billingPeriod;
    private boolean cancelAtPeriodEnd;
    private LocalDateTime subscriptionGraceEndsAt;
    private boolean manualPaymentRequired;
    private LocalDateTime currentPeriodPaidAt;
    private LocalDateTime refundEligibleUntil;
    private boolean refundEligible;
    private BigDecimal refundableAmount;
    private Integer refundCoolingDays;
    private LocalDateTime refundedAt;
    private com.ael.algoryqrservice.qrservice.model.enums.RefundStatus refundStatus;
    private BillingSnapshot billingSnapshot;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private LocalDateTime purchasedAt;
    private Integer daysUntilExpiry;
    private LocalDateTime nextPaymentDueAt;
    private boolean paymentApproaching;
    private boolean expiryApproaching;
    private boolean expired;
    private boolean usable;
}
