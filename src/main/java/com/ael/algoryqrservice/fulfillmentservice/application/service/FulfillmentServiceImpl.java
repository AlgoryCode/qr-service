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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    @Transactional(transactionManager = "fulfillmentTransactionManager")
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
        PackageFulfillment created = fulfillmentRepository.saveFulfillment(
                new PackageFulfillment(
                        null,
                        command.eventId(),
                        command.eventType(),
                        command.purchaseId(),
                        command.userId(),
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

        fulfillmentRepository.supersedeOtherFulfillments(command.userId(), created.id());
        entitlementPort.supersedeOtherEntitlements(command.userId(), created.id());
        grantPackageEntitlements(created, command.periodStart(), command.periodEnd(), now);
        fulfillmentRepository.upsertActivePackage(
                new ActivePackage(
                        null,
                        command.userId(),
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
                "Package fulfilled. fulfillmentId={} userId={} purchaseId={} packageCode={}",
                created.id(),
                command.userId(),
                command.purchaseId(),
                command.packageCode()
        );
        return created;
    }

    @Override
    @Transactional(transactionManager = "fulfillmentTransactionManager")
    public void assignPackage(Long userId, String packageCode, int periodDays) {
        if (findActivePackage(userId).isPresent()) {
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
                "assign:" + userId + ":" + plan.code(),
                "package.assigned",
                0L,
                userId,
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
    @Transactional(readOnly = true, transactionManager = "fulfillmentTransactionManager")
    public Optional<ActivePackage> findActivePackage(Long userId) {
        return fulfillmentRepository.findActiveByUserId(userId)
                .filter(active -> active.status() == PackageStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "fulfillmentTransactionManager")
    public ActivePackage requireActivePackage(Long userId) {
        return findActivePackage(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Active package not found for user " + userId));
    }

    @Override
    @Transactional(
            readOnly = true,
            transactionManager = "fulfillmentTransactionManager",
            propagation = Propagation.REQUIRES_NEW
    )
    public ActivePackageView requireActivePackageView(Long userId) {
        ActivePackage active = requireActivePackage(userId);
        List<UserEntitlement> entitlements = entitlementPort.findActiveByUserId(userId);
        String packageName = resolvePackageName(active);
        return new ActivePackageView(
                active.id(),
                active.userId(),
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
    @Transactional(readOnly = true, transactionManager = "fulfillmentTransactionManager")
    public PackageFulfillment requireFulfillment(Long fulfillmentId) {
        return fulfillmentRepository.findById(fulfillmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment not found: " + fulfillmentId));
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "fulfillmentTransactionManager")
    public Page<PackageFulfillment> listFulfillments(Long userId, Pageable pageable) {
        return fulfillmentRepository.findByUserId(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "fulfillmentTransactionManager")
    public List<UserEntitlement> listActiveEntitlements(Long userId) {
        return entitlementPort.findActiveByUserId(userId);
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
                fulfillment.userId(),
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
