package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.BillingRefundProperties;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionRefundPolicy {

    private final BillingRefundProperties properties;

    public int coolingDays(BillingPeriod billingPeriod) {
        if (billingPeriod == BillingPeriod.YEARLY) {
            return properties.getYearlyCoolingDays();
        }
        return properties.getMonthlyCoolingDays();
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
        return paidAt.plusDays(coolingDays(resolveBillingPeriod(purchase)));
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

    private BillingPeriod resolveBillingPeriod(Purchase purchase) {
        return purchase.getBillingPeriod() != null ? purchase.getBillingPeriod() : BillingPeriod.MONTHLY;
    }
}
