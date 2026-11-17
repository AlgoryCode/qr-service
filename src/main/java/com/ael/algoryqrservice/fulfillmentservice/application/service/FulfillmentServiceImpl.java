package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.AddonGrant;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackageView;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.EntitlementSource;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackageItem;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.PackageAlreadyAssignedException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.ResourceNotFoundException;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEventType;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PurchaseModulePort;
import com.ael.algoryqrservice.qrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentServiceImpl implements FulfillmentService {

    private final FulfillmentRepositoryPort fulfillmentRepository;
    private final PackageCatalogPort packageCatalogPort;
    private final EntitlementPort entitlementPort;
    private final PurchaseModulePort purchaseModulePort;
    private final com.ael.algoryqrservice.fulfillmentservice.domain.port.PlanVersionPort planVersionPort;
    private final SubscriptionEvents subscriptionEvents;

    @Override
    @Transactional
    public PackageFulfillment fulfillPaidPackage(FulfillmentCommand command) {
        Optional<PackageFulfillment> existing = fulfillmentRepository.findByEventId(command.eventId());
        if (existing.isPresent()) {
            log.info(
                    "Fulfillment already applied. eventId={} fulfillmentId={}",
                    command.eventId(),
                    existing.get().id()
            );
            return existing.get();
        }

        Instant now = Instant.now();
        PackageFulfillment created;
        try {
            created = fulfillmentRepository.saveFulfillment(
                    new PackageFulfillment(
                            null,
                            command.eventId(),
                            command.eventType(),
                            command.purchaseId(),
                            command.merchantId(),
                            command.packageId(),
                            command.packageCode(),
                            command.conversationId(),
                            command.paymentId(),
                            command.subscriptionId(),
                            command.billingCycleNumber(),
                            command.periodStart(),
                            command.periodEnd(),
                            command.amount(),
                            command.currency(),
                            FulfillmentStatus.ACTIVE,
                            now,
                            now
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            Optional<PackageFulfillment> raced = fulfillmentRepository.findByEventId(command.eventId());
            if (raced.isPresent()) {
                log.info(
                        "Fulfillment already applied after concurrent insert. eventId={} fulfillmentId={}",
                        command.eventId(),
                        raced.get().id()
                );
                return raced.get();
            }
            throw exception;
        }

        // Capacity usage is counted live from what exists, so nothing is carried between packages.
        Optional<ActivePackage> previous = fulfillmentRepository.findActiveByMerchantId(command.merchantId());
        entitlementPort.lockActiveByMerchantId(command.merchantId());
        fulfillmentRepository.supersedeOtherFulfillments(command.merchantId(), created.id());
        entitlementPort.supersedeOtherEntitlements(command.merchantId(), created.id());
        grantPackageEntitlements(created, command.periodStart(), command.periodEnd(), now);
        SubscriptionKind kind = resolveKind(command);
        fulfillmentRepository.upsertActivePackage(
                new ActivePackage(
                        null,
                        command.merchantId(),
                        created.id(),
                        command.purchaseId(),
                        command.packageId(),
                        command.packageCode(),
                        command.periodStart(),
                        command.periodEnd(),
                        PackageStatus.ACTIVE,
                        now,
                        now,
                        kind,
                        null
                )
        );
        if (kind == SubscriptionKind.PAID) {
            subscriptionEvents.emit(SubscriptionEvent.of(
                    command.merchantId(),
                    paidEventType(previous, command),
                    command.packageCode(),
                    previous.map(ActivePackage::packageCode).orElse(null),
                    PackageStatus.ACTIVE,
                    toInstant(command.periodEnd()),
                    "purchaseId=" + command.purchaseId()));
        }

        log.info(
                "Package fulfilled. fulfillmentId={} merchantId={} purchaseId={} packageCode={}",
                created.id(),
                command.merchantId(),
                command.purchaseId(),
                command.packageCode()
        );
        return created;
    }

    @Override
    @Transactional
    public void assignPackage(Long merchantId, String packageCode, int periodDays) {
        if (findActivePackage(merchantId).isPresent()) {
            throw new PackageAlreadyAssignedException();
        }
        PlanPackage plan = packageCatalogPort.findByCode(packageCode)
                .filter(PlanPackage::active)
                .orElseThrow(() -> new ResourceNotFoundException("Paket bulunamadı"));
        if (plan.items() == null || plan.items().isEmpty()) {
            throw new ResourceNotFoundException("Paket kalemleri bulunamadı");
        }
        int effectivePeriodDays = resolvePeriodDays(plan, periodDays);
        LocalDate periodStart = LocalDate.now(ZoneOffset.UTC);
        fulfillPaidPackage(new FulfillmentCommand(
                "assign:" + merchantId + ":" + plan.code(),
                "package.assigned",
                0L,
                merchantId,
                plan.id(),
                plan.code(),
                null,
                null,
                null,
                null,
                periodStart,
                periodStart.plusDays(effectivePeriodDays),
                BigDecimal.ZERO,
                plan.currency() == null ? "TRY" : plan.currency(),
                Map.of()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivePackage> findActivePackage(Long merchantId) {
        return fulfillmentRepository.findActiveByMerchantId(merchantId)
                .filter(ActivePackage::grantsAccess);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivePackage> findCurrentPackage(Long merchantId) {
        return fulfillmentRepository.findActiveByMerchantId(merchantId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyPackage(Long merchantId) {
        return fulfillmentRepository.hasAnyFulfillment(merchantId);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivePackage requireActivePackage(Long merchantId) {
        return findActivePackage(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Active package not found for user " + merchantId));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivePackageView requireActivePackageView(Long merchantId) {
        ActivePackage active = requireActivePackage(merchantId);
        List<UserEntitlement> entitlements = entitlementPort.findActiveByMerchantId(merchantId);
        String packageName = resolvePackageName(active);
        return new ActivePackageView(
                active.id(),
                active.merchantId(),
                active.fulfillmentId(),
                active.purchaseId(),
                active.packageId(),
                active.packageCode(),
                packageName,
                active.periodStart(),
                active.periodEnd(),
                active.status(),
                active.activatedAt(),
                active.updatedAt(),
                distinctSorted(entitlements.stream().map(UserEntitlement::featureCode).toList()),
                distinctSorted(entitlements.stream().map(UserEntitlement::scopeCode).toList()),
                entitlements,
                active.kind(),
                active.graceEndsAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PackageFulfillment requireFulfillment(Long fulfillmentId) {
        return fulfillmentRepository.findById(fulfillmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment not found: " + fulfillmentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PackageFulfillment> listFulfillments(Long merchantId, Pageable pageable) {
        return fulfillmentRepository.findByMerchantId(merchantId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserEntitlement> listActiveEntitlements(Long merchantId) {
        return entitlementPort.findActiveByMerchantId(merchantId);
    }

    @Override
    @Transactional
    public List<UserEntitlement> grantAddon(AddonGrant grant) {
        if (grant == null || grant.merchantId() == null || grant.purchaseId() == null) {
            return List.of();
        }
        if (entitlementPort.existsByPurchaseIdAndSource(grant.purchaseId(), EntitlementSource.ADDON_PURCHASE)) {
            log.info("Add-on already granted. purchaseId={}", grant.purchaseId());
            return List.of();
        }
        // entitlement_grants.fulfillment_id is required: hang the add-on off the package it was bought on.
        ActivePackage host = fulfillmentRepository.findActiveByMerchantId(grant.merchantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active package not found for add-on grant: merchantId=" + grant.merchantId()));
        Instant now = Instant.now();
        List<UserEntitlement> entitlements = grant.lines().stream()
                .filter(line -> line.quantity() > 0 && line.featureCode() != null && !line.featureCode().isBlank())
                .map(line -> new UserEntitlement(
                        null,
                        grant.merchantId(),
                        host.fulfillmentId(),
                        grant.purchaseId(),
                        line.productId(),
                        ProductType.ADDON_PRODUCT,
                        line.featureCode(),
                        line.scopeCode(),
                        line.quantity(),
                        false,
                        0,
                        EntitlementSource.ADDON_PURCHASE,
                        PackageStatus.ACTIVE,
                        now,
                        line.expiresAt(),
                        now,
                        now
                ))
                .toList();
        if (entitlements.isEmpty()) {
            log.warn("Add-on purchase has no grantable lines. purchaseId={}", grant.purchaseId());
            return List.of();
        }
        List<UserEntitlement> saved = entitlementPort.saveAll(entitlements);
        log.info("Add-on granted. merchantId={} purchaseId={} entitlements={}",
                grant.merchantId(), grant.purchaseId(), saved.size());
        return saved;
    }

    @Override
    @Transactional
    public int revokeAddon(Long purchaseId) {
        if (purchaseId == null) {
            return 0;
        }
        int revoked = entitlementPort.changeStatusByPurchaseId(
                purchaseId, EntitlementSource.ADDON_PURCHASE, PackageStatus.CANCELLED);
        if (revoked > 0) {
            log.info("Add-on revoked. purchaseId={} entitlements={}", purchaseId, revoked);
        }
        return revoked;
    }

    @Override
    @Transactional
    public int extendAddon(Long purchaseId, Instant expiresAt) {
        if (purchaseId == null || expiresAt == null) {
            return 0;
        }
        return entitlementPort.extendAddonExpiry(purchaseId, expiresAt);
    }

    private void grantPackageEntitlements(
            PackageFulfillment fulfillment,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant now
    ) {
        Optional<PlanPackage> planPackage = resolvePlanPackage(fulfillment.packageId(), fulfillment.packageCode());
        if (planPackage.isEmpty()) {
            log.warn(
                    "Package definition missing while granting entitlements. packageId={} packageCode={}",
                    fulfillment.packageId(),
                    fulfillment.packageCode()
            );
            return;
        }

        Instant startsAt = toInstant(periodStart);
        Instant expiresAt = toInstant(periodEnd);
        // A purchase grants the version it was bought on, so editing the package later never changes
        // an existing subscriber's rights. Trials, grants and the free package have no purchase pin.
        List<PlanPackageItem> lines = planVersionPort.linesOfPurchase(fulfillment.purchaseId())
                .orElseGet(() -> planPackage.get().items());
        List<UserEntitlement> entitlements = new ArrayList<>(lines.stream()
                .map(item -> toEntitlement(fulfillment, item, startsAt, expiresAt, now))
                .toList());
        // Modules bought together with the package are paid for in the same charge, so they live
        // and renew exactly like the package's own lines.
        purchaseModulePort.modulesOf(fulfillment.purchaseId()).stream()
                .filter(line -> line.featureCode() != null && !line.featureCode().isBlank())
                .map(line -> new UserEntitlement(
                        null,
                        fulfillment.merchantId(),
                        fulfillment.id(),
                        fulfillment.purchaseId(),
                        line.productId(),
                        ProductType.ADDON_PRODUCT,
                        line.featureCode(),
                        line.scopeCode(),
                        line.quantity(),
                        false,
                        0,
                        EntitlementSource.PACKAGE_MODULE,
                        PackageStatus.ACTIVE,
                        startsAt,
                        expiresAt,
                        now,
                        now
                ))
                .forEach(entitlements::add);
        entitlementPort.saveAll(entitlements);
    }

    private static SubscriptionEventType paidEventType(Optional<ActivePackage> previous, FulfillmentCommand command) {
        if (previous.isEmpty() || previous.get().kind() != SubscriptionKind.PAID) {
            return SubscriptionEventType.PACKAGE_ACTIVATED;
        }
        if (previous.get().status() == PackageStatus.GRACE_PERIOD) {
            return SubscriptionEventType.PAYMENT_RECOVERED;
        }
        if (Objects.equals(previous.get().purchaseId(), command.purchaseId())) {
            return SubscriptionEventType.PACKAGE_RENEWED;
        }
        return SubscriptionEventType.PACKAGE_CHANGED;
    }

    static SubscriptionKind resolveKind(FulfillmentCommand command) {
        String code = command.packageCode();
        if (CatalogPackages.FREE_PACKAGE.equals(code)) {
            return SubscriptionKind.FREE;
        }
        if (CatalogPackages.ULTIMATE_TRIAL_PACKAGE.equals(code)) {
            return SubscriptionKind.TRIAL;
        }
        boolean paid = command.amount() != null && command.amount().signum() > 0
                && command.purchaseId() != null && command.purchaseId() > 0;
        return paid ? SubscriptionKind.PAID : SubscriptionKind.GRANT;
    }

    private UserEntitlement toEntitlement(
            PackageFulfillment fulfillment,
            PlanPackageItem item,
            Instant startsAt,
            Instant expiresAt,
            Instant now
    ) {
        String featureCode = item.featureCode() != null ? item.featureCode() : item.productCode();
        return new UserEntitlement(
                null,
                fulfillment.merchantId(),
                fulfillment.id(),
                fulfillment.purchaseId(),
                item.productId(),
                item.productType(),
                featureCode,
                item.scopeCode(),
                item.quantity() == null ? 0 : item.quantity(),
                item.unlimited(),
                0,
                EntitlementSource.PACKAGE_INCLUDE,
                PackageStatus.ACTIVE,
                startsAt,
                expiresAt,
                now,
                now
        );
    }

    private Optional<PlanPackage> resolvePlanPackage(Long packageId, String packageCode) {
        if (packageId != null) {
            Optional<PlanPackage> byId = packageCatalogPort.findById(packageId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (packageCode == null || packageCode.isBlank()) {
            return Optional.empty();
        }
        return packageCatalogPort.findByCode(packageCode);
    }

    private String resolvePackageName(ActivePackage active) {
        return resolvePlanPackage(active.packageId(), active.packageCode())
                .map(PlanPackage::name)
                .orElse(null);
    }

    private static int resolvePeriodDays(PlanPackage plan, int periodDays) {
        if (periodDays >= 1) {
            return periodDays;
        }
        if (plan.validityDays() != null && plan.validityDays() >= 1) {
            return plan.validityDays();
        }
        throw new ResourceNotFoundException("Paket süresi geçersiz");
    }

    private static Instant toInstant(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static List<String> distinctSorted(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
