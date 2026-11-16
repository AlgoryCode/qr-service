package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEventType;
import com.ael.algoryqrservice.qrservice.billing.service.entitlement.AddonEntitlementService;
import com.ael.algoryqrservice.qrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recurring modules share the package's billing date: bought mid-period they are charged only for
 * the days left, and from then on their full price is part of the package's renewal charge.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringAddonBilling {

    /** A recurring module that lapsed with a late renewal is picked up again within this window. */
    static final Duration RENEWAL_WINDOW = Duration.ofDays(14);

    private final PurchaseRepository purchaseRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final AddonEntitlementService addonEntitlementService;

    /**
     * Share of a full-period price for the days left in the host's current period, rounded HALF_UP
     * to kuruş. A host without an end date, or a period already over, pays the full price.
     */
    public static BigDecimal prorate(BigDecimal fullPeriod, Purchase host, LocalDateTime now) {
        if (fullPeriod == null || fullPeriod.signum() <= 0 || host == null || host.getExpiresAt() == null) {
            return fullPeriod;
        }
        LocalDateTime end = host.getExpiresAt();
        int months = host.getBillingIntervalMonths() == null || host.getBillingIntervalMonths() < 1
                ? 1
                : host.getBillingIntervalMonths();
        long periodDays = Math.max(1, Duration.between(end.minusMonths(months), end).toDays());
        long remainingHours = Duration.between(now, end).toHours();
        if (remainingHours <= 0) {
            return fullPeriod;
        }
        long remainingDays = Math.min(periodDays, (remainingHours + 23) / 24);
        BigDecimal share = fullPeriod.multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
        return share.max(new BigDecimal("0.01"));
    }

    /** What a host purchase's renewal charges: the package (with its own modules) plus later recurring modules. */
    public static BigDecimal renewalAmount(Purchase host) {
        BigDecimal base = host.getRecurringPrice() != null ? host.getRecurringPrice() : host.getPrice();
        BigDecimal addons = host.getAddonRecurringAmount() == null ? BigDecimal.ZERO : host.getAddonRecurringAmount();
        return (base == null ? BigDecimal.ZERO : base).add(addons);
    }

    public static boolean renewsWithHost(Purchase host) {
        return host != null
                && host.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                && host.getSubscriptionId() != null
                && !host.getSubscriptionId().isBlank()
                && !host.isCancelAtPeriodEnd();
    }

    /** Recomputes the merchant's recurring module total and the subscription's renewal charge. */
    @Transactional
    public void sync(Long merchantId) {
        findHost(merchantId).ifPresent(this::syncHost);
    }

    /** Same as {@link #sync} for a known host purchase; safe to call repeatedly. */
    @Transactional
    public void syncHost(Purchase host) {
        if (host == null || host.getMerchantId() == null || !renewsWithHost(host)) {
            return;
        }
        LocalDateTime now = AppTime.nowLocal();
        BigDecimal addons = recurringAddons(host.getMerchantId(), now).stream()
                .map(Purchase::getRecurringPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        host.setAddonRecurringAmount(addons);
        purchaseRepository.save(host);
        BigDecimal amount = renewalAmount(host);
        boolean switched = paymentServiceClient.switchSubscriptionPlan(
                host.getMerchantId(),
                host.getSubscriptionId(),
                host.getId(),
                amount,
                Map.of("recurringPrice", amount, "addonRecurringAmount", addons)
        );
        if (!switched) {
            log.warn("recurring_addon_sync_skipped merchantId={} purchaseId={} subscriptionId={}",
                    host.getMerchantId(), host.getId(), host.getSubscriptionId());
            return;
        }
        log.info("recurring_addon_sync merchantId={} hostPurchaseId={} addons={} renewalAmount={}",
                host.getMerchantId(), host.getId(), addons, amount);
    }

    /** The package renewed: recurring modules follow it to the new period end. */
    @EventListener
    public void onSubscriptionEvent(SubscriptionEvent event) {
        if (event.type() != SubscriptionEventType.PACKAGE_RENEWED || event.endsAt() == null) {
            return;
        }
        LocalDateTime newEnd = LocalDateTime.ofInstant(event.endsAt(), AppTime.ZONE);
        for (Purchase addon : recurringAddons(event.merchantId(), AppTime.nowLocal())) {
            if (addon.getExpiresAt() != null && !addon.getExpiresAt().isBefore(newEnd)) {
                continue;
            }
            addon.setExpiresAt(newEnd);
            addon.setStatus(PurchaseStatus.ACTIVE);
            purchaseRepository.save(addon);
            addonEntitlementService.extend(addon);
            log.info("recurring_addon_renewed merchantId={} purchaseId={} expiresAt={}",
                    addon.getMerchantId(), addon.getId(), newEnd);
        }
    }

    /** Recurring add-ons still billed with the package: active, or lapsed only by a late renewal. */
    List<Purchase> recurringAddons(Long merchantId, LocalDateTime now) {
        LocalDateTime lapsedAfter = now.minus(RENEWAL_WINDOW);
        return purchaseRepository.findByMerchantIdOrderByPurchasedAtDesc(merchantId).stream()
                .filter(p -> p.getPurchaseType() == PurchaseType.ADD_ON)
                .filter(p -> p.getRecurringPrice() != null && p.getRecurringPrice().signum() > 0)
                .filter(p -> p.getStatus() == PurchaseStatus.ACTIVE
                        || (p.getStatus() == PurchaseStatus.EXPIRED
                        && p.getExpiresAt() != null && p.getExpiresAt().isAfter(lapsedAfter)))
                .toList();
    }

    private Optional<Purchase> findHost(Long merchantId) {
        return purchaseRepository.findByMerchantIdAndStatus(merchantId, PurchaseStatus.ACTIVE).stream()
                .filter(p -> p.getPurchaseType() != PurchaseType.ADD_ON)
                .filter(p -> p.getPackageId() != null)
                .filter(RecurringAddonBilling::renewsWithHost)
                .max(Comparator.comparing(Purchase::getExpiresAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    }
}
