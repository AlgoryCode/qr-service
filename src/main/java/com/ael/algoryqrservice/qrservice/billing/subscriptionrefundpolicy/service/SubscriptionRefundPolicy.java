package com.ael.algoryqrservice.qrservice.billing.subscriptionrefundpolicy.service;

import com.ael.algoryqrservice.qrservice.config.BillingRefundProperties;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.qrservice.util.BillingPeriodResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionRefundPolicy {

    private final BillingRefundProperties properties;

    public int coolingDays(BillingPeriod billingPeriod) {
        if (billingPeriod == null) {
            return properties.getMonthlyCoolingDays();
        }
        return switch (billingPeriod) {
            case YEARLY -> properties.getYearlyCoolingDays();
            case MONTHLY, ONE_TIME -> properties.getMonthlyCoolingDays();
        };
    }

    public LocalDateTime resolvePeriodPaidAt(Purchase purchase) {
        if (purchase.getCurrentPeriodPaidAt() != null) {
            return purchase.getCurrentPeriodPaidAt();
        }
        if (purchase.getStartsAt() != null) {
            return purchase.getStartsAt();
        }
        return purchase.getPurchasedAt();
    }

    public LocalDateTime refundEligibleUntil(Purchase purchase) {
        LocalDateTime paidAt = resolvePeriodPaidAt(purchase);
        if (paidAt == null) {
            return null;
        }
        return paidAt.plusDays(coolingDays(BillingPeriodResolver.resolve(purchase)));
    }

    public boolean isWithinCoolingWindow(Purchase purchase, LocalDateTime now) {
        LocalDateTime eligibleUntil = refundEligibleUntil(purchase);
        if (eligibleUntil == null) {
            return false;
        }
        return !now.isAfter(eligibleUntil);
    }

    public boolean isRefundEligible(Purchase purchase, LocalDateTime now) {
        if (purchase.getRefundedAt() != null) {
            return false;
        }
        return isWithinCoolingWindow(purchase, now);
    }
}
