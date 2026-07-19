package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.model.BillingSnapshot;
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
    private Long paymentMethodId;
    private String cardBrand;
    private String cardLastFour;
    private String subscriptionId;
    private SubscriptionStatus subscriptionStatus;
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
