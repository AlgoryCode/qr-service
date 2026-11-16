package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.port.SubscriptionHistoryPort;
import com.ael.algoryqrservice.fulfillmentservice.config.BillingLifecycleProperties;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEventType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.qrservice.catalog.CatalogPackages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moves a merchant's subscription between its states. The rule it enforces: once a merchant has a
 * package, they always have one. A trial, a grant or a paid package that ends (or whose payment
 * grace runs out, or that is refunded) falls back to the permanent free package instead of locking
 * the account. Nothing the merchant created is deleted; limits of the free package simply apply.
 *
 * <pre>
 *   (none) --startTrial--> TRIAL --period end--> FREE
 *   PAID --payment failed--> PAID/GRACE_PERIOD --paid--> PAID/ACTIVE
 *                                     \--grace end--> FREE
 *   PAID --period end without renewal / refund--> FREE
 *   any --purchase paid--> PAID
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionLifecycleService {

    private final FulfillmentRepositoryPort repository;
    private final FulfillmentService fulfillmentService;
    private final PackageCatalogPort packageCatalog;
    private final SubscriptionEvents events;
    private final BillingLifecycleProperties properties;
    private final SubscriptionHistoryPort history;

    /**
     * Gives a brand-new merchant the full trial. A merchant that ever had any package (including a
     * previous trial) gets nothing, so the trial cannot be restarted.
     *
     * @return whether a trial was started
     */
    @Transactional
    public boolean startTrial(Long merchantId) {
        if (merchantId == null || repository.hasAnyFulfillment(merchantId)) {
            return false;
        }
        Optional<PlanPackage> trial = packageCatalog.findByCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .filter(PlanPackage::active);
        if (trial.isEmpty()) {
            log.error("Trial package missing or inactive; merchant starts without a package. merchantId={}", merchantId);
            return false;
        }
        LocalDate start = today();
        LocalDate end = start.plusDays(properties.trialDays());
        fulfillmentService.fulfillPaidPackage(command(
                "trial:" + merchantId, "package.trial_started", merchantId, trial.get(), start, end));
        events.emit(SubscriptionEvent.of(merchantId, SubscriptionEventType.TRIAL_STARTED,
                trial.get().code(), null, PackageStatus.ACTIVE, endOf(end), null));
        return true;
    }

    /**
     * A renewal payment failed: keep the paid package open for the grace period. Ignored unless the
     * failure is about the merchant's current paid package.
     */
    @Transactional
    public void enterGrace(Long merchantId, Long purchaseId, Instant graceEndsAt, String reason) {
        Optional<ActivePackage> current = repository.findActiveByMerchantId(merchantId);
        if (current.isEmpty() || current.get().kind() != SubscriptionKind.PAID) {
            return;
        }
        ActivePackage active = current.get();
        if (purchaseId != null && active.purchaseId() != null && !purchaseId.equals(active.purchaseId())) {
            log.info("Payment failure for a non-current purchase ignored. merchantId={} purchaseId={} current={}",
                    merchantId, purchaseId, active.purchaseId());
            return;
        }
        if (active.status() == PackageStatus.GRACE_PERIOD) {
            return;
        }
        if (active.status() != PackageStatus.ACTIVE) {
            return;
        }
        Instant endsAt = graceEndsAt != null
                ? graceEndsAt
                : Instant.now().plus(Duration.ofDays(properties.graceDays()));
        repository.saveActivePackage(active.withStatus(PackageStatus.GRACE_PERIOD, endsAt));
        events.emit(SubscriptionEvent.of(merchantId, SubscriptionEventType.PAYMENT_FAILED,
                active.packageCode(), null, PackageStatus.GRACE_PERIOD, endsAt, reason));
    }

    /** The current package's purchase was refunded: it stops now and the merchant falls back to free. */
    @Transactional
    public void onRefund(Long merchantId, Long purchaseId) {
        Optional<ActivePackage> current = repository.findActiveByMerchantId(merchantId);
        if (current.isEmpty() || purchaseId == null || !purchaseId.equals(current.get().purchaseId())) {
            return;
        }
        events.emit(SubscriptionEvent.of(merchantId, SubscriptionEventType.PACKAGE_REFUNDED,
                current.get().packageCode(), null, PackageStatus.CANCELLED, null, null));
        downgradeToFree(merchantId, "refunded");
    }

    /**
     * Replaces whatever the merchant is on with the free package. Idempotent per outgoing package.
     *
     * @return whether the merchant was moved
     */
    @Transactional
    public boolean downgradeToFree(Long merchantId, String reason) {
        Optional<ActivePackage> current = repository.findActiveByMerchantId(merchantId);
        if (current.isPresent() && current.get().kind() == SubscriptionKind.FREE && current.get().grantsAccess()) {
            return false;
        }
        Optional<PlanPackage> free = packageCatalog.findByCode(CatalogPackages.FREE_PACKAGE).filter(PlanPackage::active);
        if (free.isEmpty()) {
            // Without a free package the old behaviour stays: the account closes until a purchase.
            current.filter(ActivePackage::grantsAccess).ifPresent(active ->
                    repository.saveActivePackage(active.withStatus(PackageStatus.EXPIRED, null)));
            log.error("FREE_PACKAGE missing or inactive; merchant closed instead of downgraded. merchantId={}", merchantId);
            return false;
        }
        String previous = current.map(ActivePackage::packageCode).orElse(null);
        String seed = current.map(active -> String.valueOf(active.fulfillmentId())).orElse("none");
        LocalDate start = today();
        fulfillmentService.fulfillPaidPackage(command(
                "free:" + merchantId + ":" + seed, "package.downgraded", merchantId, free.get(), start, null));
        SubscriptionEventType type = current.map(ActivePackage::kind).orElse(null) == SubscriptionKind.TRIAL
                ? SubscriptionEventType.TRIAL_ENDED
                : SubscriptionEventType.DOWNGRADED_TO_FREE;
        events.emit(SubscriptionEvent.of(merchantId, type, free.get().code(), previous,
                PackageStatus.ACTIVE, null, reason));
        return true;
    }

    /**
     * Packages whose state must move on now: trials and grants past their last day, paid packages
     * that were not renewed, and grace periods that ran out. The job moves each one with
     * {@link #downgradeToFree} in its own transaction so one failure does not stop the rest.
     */
    @Transactional(readOnly = true)
    public List<ActivePackage> findDue(int limit) {
        return repository.findLifecycleDue(today(), Instant.now(), limit);
    }

    /** Emits one TRIAL_ENDING event per configured reminder day; replays are dropped by dedupe key. */
    @Transactional
    public int remindEndingTrials() {
        LocalDate today = today();
        int sent = 0;
        for (Integer daysBefore : properties.trialReminderDaysBefore()) {
            LocalDate lastDay = today.plusDays(daysBefore);
            for (ActivePackage trial : repository.findTrialsEndingBetween(lastDay, lastDay)) {
                SubscriptionEvent event = SubscriptionEvent.of(trial.merchantId(), SubscriptionEventType.TRIAL_ENDING,
                                trial.packageCode(), null, trial.status(), endOf(trial.periodEnd()),
                                daysBefore + " days left")
                        .withDedupeKey("trial-ending:" + trial.merchantId() + ":" + trial.fulfillmentId() + ":" + daysBefore);
                if (events.emit(event)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /** Newest first; the merchant-facing timeline hides internal reminder events. */
    @Transactional(readOnly = true)
    public List<SubscriptionEvent> history(Long merchantId, int limit) {
        return history.findByMerchant(merchantId, limit).stream()
                .filter(event -> event.type() != SubscriptionEventType.TRIAL_ENDING)
                .toList();
    }

    public static String dueReason(ActivePackage active) {
        if (active.status() == PackageStatus.GRACE_PERIOD) {
            return "grace_period_expired";
        }
        return switch (active.kind() == null ? SubscriptionKind.PAID : active.kind()) {
            case TRIAL -> "trial_ended";
            case GRANT -> "grant_ended";
            case PAID -> "period_ended_without_renewal";
            case FREE -> "free";
        };
    }

    private static FulfillmentCommand command(
            String eventId,
            String eventType,
            Long merchantId,
            PlanPackage plan,
            LocalDate start,
            LocalDate end
    ) {
        return new FulfillmentCommand(
                eventId, eventType, 0L, merchantId, plan.id(), plan.code(),
                null, null, null, null, start, end, BigDecimal.ZERO,
                plan.currency() == null ? "TRY" : plan.currency(), Map.of());
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static Instant endOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
