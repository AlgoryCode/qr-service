package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackageView;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.EntitlementSource;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackageItem;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.PackageAlreadyAssignedException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.ResourceNotFoundException;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.EntitlementPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
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

        fulfillmentRepository.supersedeOtherFulfillments(command.merchantId(), created.id());
        entitlementPort.supersedeOtherEntitlements(command.merchantId(), created.id());
        grantPackageEntitlements(created, command.periodStart(), command.periodEnd(), now);
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
                        now
                )
        );

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
        return fulfillmentRepository.findActiveByUserId(merchantId)
                .filter(active -> active.status() == PackageStatus.ACTIVE);
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
        List<UserEntitlement> entitlements = entitlementPort.findActiveByUserId(merchantId);
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
                entitlements
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
        return entitlementPort.findActiveByUserId(merchantId);
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
        List<UserEntitlement> entitlements = planPackage.get().items().stream()
                .map(item -> toEntitlement(fulfillment, item, startsAt, expiresAt, now))
                .toList();
        entitlementPort.saveAll(entitlements);
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
