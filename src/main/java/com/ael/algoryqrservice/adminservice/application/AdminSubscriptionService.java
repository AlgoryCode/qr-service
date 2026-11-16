package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminPackageDtos;
import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.application.service.SubscriptionEvents;
import com.ael.algoryqrservice.fulfillmentservice.application.service.SubscriptionLifecycleService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEventType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.qrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.purchase.lifecycle.RemoteSubscriptionCanceller;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Admin actions on a merchant's subscription, on the fulfillment lifecycle model: the merchant's
 * current package is the fulfillment {@code active_package} row, and ending anything means falling
 * back to the free package through {@link SubscriptionLifecycleService#downgradeToFree}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSubscriptionService {

    static final String ACTOR = "admin";
    private static final int FULFILLMENT_LOOKBACK = 50;
    private static final int CATALOG_PAGE = 100;

    private final FulfillmentService fulfillmentService;
    private final SubscriptionLifecycleService lifecycleService;
    private final PackageCatalogPort packageCatalog;
    private final SubscriptionEvents events;
    private final PurchaseRepository purchaseRepository;
    private final RemoteSubscriptionCanceller subscriptionCanceller;

    /**
     * Moves the current trial's last day {@code days} later. The trial package is fulfilled again
     * with the new period so the trial's entitlements (whose expiry is the period end) move along
     * and the usage counters carry over; the kind stays TRIAL.
     */
    @Transactional
    public AdminPackageDtos.ExtendTrialResponse extendTrial(Long merchantId, int days) {
        requireDays(days, 365);
        ActivePackage trial = requireCurrentTrial(merchantId);
        LocalDate today = today();
        LocalDate base = trial.periodEnd() == null || trial.periodEnd().isBefore(today) ? today : trial.periodEnd();
        LocalDate newEnd = base.plusDays(days);
        PlanPackage plan = resolvePlan(trial.packageId(), trial.packageCode())
                .orElseThrow(() -> new BadRequestException("Deneme paketi katalogda bulunamadı"));

        fulfillmentService.fulfillPaidPackage(new FulfillmentCommand(
                "admin-trial-extend:" + merchantId + ":" + System.currentTimeMillis(),
                "package.trial_extended",
                trial.purchaseId() == null ? 0L : trial.purchaseId(),
                merchantId,
                plan.id(),
                plan.code(),
                null, null, null, null,
                trial.periodStart() == null ? today : trial.periodStart(),
                newEnd,
                BigDecimal.ZERO,
                currency(plan),
                Map.of("actor", ACTOR, "daysAdded", days)
        ));
        log.info("Admin extended trial. merchantId={} days={} newEnd={}", merchantId, days, newEnd);
        return AdminPackageDtos.ExtendTrialResponse.builder()
                .purchaseId(purchaseIdOf(trial.purchaseId()))
                .packageName(plan.name())
                .expiresAt(endOf(newEnd))
                .daysAdded(days)
                .build();
    }

    /** Ends the current trial now; the merchant falls back to the free package. */
    @Transactional
    public AdminPackageDtos.EndTrialResponse endTrial(Long merchantId) {
        ActivePackage trial = requireCurrentTrial(merchantId);
        lifecycleService.downgradeToFree(merchantId, "admin_ended_trial");
        return AdminPackageDtos.EndTrialResponse.builder()
                .purchaseId(purchaseIdOf(trial.purchaseId()))
                .packageName(packageName(trial))
                .expiresAt(now())
                .build();
    }

    /**
     * Ends the current package now; the merchant falls back to the free package. A recurring
     * subscription behind it stops renewing too, otherwise the next charge would bring it back.
     */
    @Transactional
    public AdminPackageDtos.PackageLifecycleResponse deactivatePackage(Long merchantId) {
        ActivePackage current = fulfillmentService.findCurrentPackage(merchantId)
                .filter(ActivePackage::grantsAccess)
                .orElseThrow(() -> new BadRequestException("Kullanıcının aktif paketi yok"));
        if (current.kind() == SubscriptionKind.FREE) {
            throw new BadRequestException("Kullanıcı zaten ücretsiz pakette");
        }
        lifecycleService.downgradeToFree(merchantId, "admin_deactivated");
        if (current.purchaseId() != null) {
            purchaseRepository.findById(current.purchaseId()).ifPresent(subscriptionCanceller::cancelIfNeeded);
        }
        return AdminPackageDtos.PackageLifecycleResponse.builder()
                .purchaseId(purchaseIdOf(current.purchaseId()))
                .packageName(packageName(current))
                .status("EXPIRED")
                .expiresAt(now())
                .build();
    }

    /**
     * Grants the merchant's last paid package (or, if they never paid, the highest-priority
     * purchasable package) for {@code days} days as an operator GRANT. When it ends the lifecycle
     * job moves the merchant to the free package.
     */
    @Transactional
    public AdminPackageDtos.PackageLifecycleResponse grantPackage(Long merchantId, int days) {
        requireDays(days, 3650);
        if (merchantId == null) {
            throw new BadRequestException("Kullanıcı kimliği gerekli");
        }
        PlanPackage plan = lastPaidPackage(merchantId)
                .or(this::highestPriorityPurchasable)
                .orElseThrow(() -> new BadRequestException("Verilebilecek bir paket bulunamadı"));
        Optional<ActivePackage> previous = fulfillmentService.findCurrentPackage(merchantId);
        LocalDate start = today();
        LocalDate end = start.plusDays(days);

        fulfillmentService.fulfillPaidPackage(new FulfillmentCommand(
                "admin-grant:" + merchantId + ":" + System.currentTimeMillis(),
                "package.admin_granted",
                0L,
                merchantId,
                plan.id(),
                plan.code(),
                null, null, null, null,
                start,
                end,
                BigDecimal.ZERO,
                currency(plan),
                Map.of("actor", ACTOR, "days", days)
        ));
        events.emit(SubscriptionEvent.of(
                        merchantId,
                        SubscriptionEventType.PACKAGE_ACTIVATED,
                        plan.code(),
                        previous.map(ActivePackage::packageCode).orElse(null),
                        PackageStatus.ACTIVE,
                        endOf(end).toInstant(ZoneOffset.UTC),
                        "admin_grant days=" + days)
                .withActor(ACTOR));
        log.info("Admin granted package. merchantId={} package={} days={}", merchantId, plan.code(), days);
        return AdminPackageDtos.PackageLifecycleResponse.builder()
                .purchaseId(null)
                .packageName(plan.name())
                .status("ACTIVE")
                .expiresAt(endOf(end))
                .daysAdded(days)
                .build();
    }

    private ActivePackage requireCurrentTrial(Long merchantId) {
        ActivePackage current = fulfillmentService.findCurrentPackage(merchantId)
                .orElseThrow(() -> new BadRequestException("Kullanıcının paketi yok"));
        if (current.kind() != SubscriptionKind.TRIAL) {
            throw new BadRequestException("Kullanıcının mevcut paketi deneme paketi değil");
        }
        return current;
    }

    /** The newest package the merchant actually paid for (trial, free and grants excluded). */
    private Optional<PlanPackage> lastPaidPackage(Long merchantId) {
        return fulfillmentService.listFulfillments(merchantId, PageRequest.of(0, FULFILLMENT_LOOKBACK))
                .stream()
                .filter(AdminSubscriptionService::isPaid)
                .map(fulfillment -> resolvePlan(fulfillment.packageId(), fulfillment.packageCode())
                        .filter(AdminSubscriptionService::isGrantable))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<PlanPackage> highestPriorityPurchasable() {
        return packageCatalog.findPurchasable(PageRequest.of(0, CATALOG_PAGE)).stream()
                .filter(AdminSubscriptionService::isGrantable)
                .max(Comparator.comparingInt(plan -> plan.priority() == null ? 0 : plan.priority()));
    }

    private static boolean isPaid(PackageFulfillment fulfillment) {
        return fulfillment.amount() != null && fulfillment.amount().signum() > 0
                && fulfillment.purchaseId() != null && fulfillment.purchaseId() > 0;
    }

    /** Trial and free packages have their own kinds and cannot be handed out as a grant. */
    private static boolean isGrantable(PlanPackage plan) {
        return plan.active()
                && !CatalogPackages.FREE_PACKAGE.equals(plan.code())
                && !CatalogPackages.ULTIMATE_TRIAL_PACKAGE.equals(plan.code());
    }

    private Optional<PlanPackage> resolvePlan(Long packageId, String packageCode) {
        if (packageId != null) {
            Optional<PlanPackage> byId = packageCatalog.findById(packageId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (packageCode == null || packageCode.isBlank()) {
            return Optional.empty();
        }
        return packageCatalog.findByCode(packageCode);
    }

    private String packageName(ActivePackage active) {
        return resolvePlan(active.packageId(), active.packageCode())
                .map(PlanPackage::name)
                .orElse(active.packageCode());
    }

    private static void requireDays(int days, int max) {
        if (days < 1 || days > max) {
            throw new BadRequestException("days 1 ile " + max + " arasında olmalı");
        }
    }

    private static Long purchaseIdOf(Long purchaseId) {
        return purchaseId == null || purchaseId <= 0 ? null : purchaseId;
    }

    private static String currency(PlanPackage plan) {
        return plan.currency() == null ? "TRY" : plan.currency();
    }

    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Entitlements of a period expire at the start of its end date (UTC), as the lifecycle does. */
    private static LocalDateTime endOf(LocalDate date) {
        return date.atStartOfDay();
    }
}
