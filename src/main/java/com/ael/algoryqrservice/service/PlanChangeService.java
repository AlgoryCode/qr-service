package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.PlanChange;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PlanChangeOptionResponse;
import com.ael.algoryqrservice.model.dto.PlanChangePackageSummary;
import com.ael.algoryqrservice.model.dto.PlanChangePreviewResponse;
import com.ael.algoryqrservice.model.dto.PlanChangeRequest;
import com.ael.algoryqrservice.model.dto.PlanChangeResponse;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PlanChangeDirection;
import com.ael.algoryqrservice.model.enums.PlanChangeStatus;
import com.ael.algoryqrservice.model.enums.PlanChangeTiming;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PlanChangeRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanChangeService {

    private static final String ENTITLEMENTS_POLICY = "REPLACE_NO_CARRYOVER";
    private static final String NO_CARRYOVER_WARNING =
            "Onceki paketten hak devri yoktur; yeni paketin haklari sifirdan tanimlanir.";
    private static final String REFUND_CLAMPED_WARNING =
            "Iade tutari onceki odemedeki kalan bakiyeyle sinirlandi.";

    private final PlanChangeRepository planChangeRepository;
    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository planPackageRepository;
    private final UserRepository userRepository;
    private final PlanPackageService planPackageService;
    private final PaymentServiceClient paymentServiceClient;
    private final PaymentRequestMapper paymentRequestMapper;
    private final PurchaseFulfillmentService purchaseFulfillmentService;
    private final PackageActivationService packageActivationService;
    private final EntitlementService entitlementService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final PurchaseLogService purchaseLogService;
    private final AppProperties appProperties;

    private static final String DOWNGRADE_NEXT_PERIOD_ONLY =
            "Paket dusurme yalnizca donem sonunda yapilabilir.";

    @Transactional(readOnly = true)
    public PlanChangePreviewResponse preview(Long userId, Long toPackageId) {
        Purchase current = requireUsablePaidPurchase(userId);
        PlanPackage fromPackage = planPackageService.findPackage(current.getPackageId());
        PlanPackage toPackage = requireTargetPackage(toPackageId, current.getPackageId());
        BillingPeriod currentPeriod = resolveBillingPeriod(current);
        BigDecimal fromPeriodPrice = periodPrice(fromPackage, currentPeriod);
        BigDecimal toPeriodPrice = periodPrice(toPackage, currentPeriod);
        PlanChangeDirection direction = resolveDirection(fromPeriodPrice, toPeriodPrice);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodEnd = current.getExpiresAt();
        double fraction = remainingFraction(current, now);

        MoneyDelta immediateDelta = resolveProratedUpgradeDelta(fromPeriodPrice, toPeriodPrice, fraction);
        List<String> warnings = new ArrayList<>();
        warnings.add(NO_CARRYOVER_WARNING);
        if (direction == PlanChangeDirection.DOWNGRADE) {
            warnings.add(DOWNGRADE_NEXT_PERIOD_ONLY);
        }

        List<PlanChangeOptionResponse> options = new ArrayList<>();
        if (direction != PlanChangeDirection.DOWNGRADE) {
            options.add(PlanChangeOptionResponse.builder()
                    .timing(PlanChangeTiming.IMMEDIATE)
                    .chargeNow(immediateDelta.chargeAmount())
                    .refundNow(BigDecimal.ZERO)
                    .chargeAtEffective(BigDecimal.ZERO)
                    .effectiveAt(now)
                    .entitlementsPolicy(ENTITLEMENTS_POLICY)
                    .build());
        }
        options.add(PlanChangeOptionResponse.builder()
                .timing(PlanChangeTiming.NEXT_PERIOD)
                .chargeNow(BigDecimal.ZERO)
                .refundNow(BigDecimal.ZERO)
                .chargeAtEffective(toPeriodPrice)
                .effectiveAt(periodEnd)
                .entitlementsPolicy(ENTITLEMENTS_POLICY)
                .build());

        return PlanChangePreviewResponse.builder()
                .fromPurchaseId(current.getId())
                .fromPackage(toSummary(fromPackage))
                .toPackage(toSummary(toPackage))
                .direction(direction)
                .currentExpiresAt(periodEnd)
                .options(List.copyOf(options))
                .warnings(List.copyOf(warnings))
                .hasScheduledChange(planChangeRepository.existsByUserIdAndStatus(userId, PlanChangeStatus.SCHEDULED))
                .build();
    }

    @Transactional
    public PlanChangeResponse request(User user, PlanChangeRequest request, String clientIp) {
        if (!Boolean.TRUE.equals(request.getWarningAck())) {
            throw new BadRequestException("Hak devri uyarisi onaylanmalidir");
        }
        Purchase current = requireUsablePaidPurchase(user.getId());
        PlanPackage fromPackage = planPackageService.findPackage(current.getPackageId());
        PlanPackage toPackage = requireTargetPackage(request.getToPackageId(), current.getPackageId());
        BillingPeriod fromPeriod = resolveBillingPeriod(current);
        BillingPeriod toPeriod = request.getBillingPeriod() != null ? request.getBillingPeriod() : fromPeriod;
        if (fromPeriod == BillingPeriod.YEARLY && toPeriod == BillingPeriod.MONTHLY
                && request.getTiming() == PlanChangeTiming.IMMEDIATE) {
            throw new BadRequestException("Yilliktan ayliga gecis yalnizca donem sonunda yapilabilir");
        }

        BigDecimal fromPeriodPrice = periodPrice(fromPackage, fromPeriod);
        BigDecimal toPeriodPrice = periodPrice(toPackage, toPeriod);
        PlanChangeDirection direction = resolveDirection(fromPeriodPrice, toPeriodPrice);

        if (planChangeRepository.existsByUserIdAndStatus(user.getId(), PlanChangeStatus.SCHEDULED)) {
            throw new BadRequestException("Zaten planlanmis bir paket gecisi var; once iptal edin");
        }
        if (planChangeRepository.existsByUserIdAndStatus(user.getId(), PlanChangeStatus.PENDING_PAYMENT)) {
            throw new BadRequestException("Odeme bekleyen bir paket gecisi var");
        }

        purchaseLogService.log(
                current.getId(),
                user.getId(),
                PurchaseLogAction.PLAN_CHANGE_REQUESTED,
                direction + " " + request.getTiming() + ": " + fromPackage.getCode()
                        + " -> " + toPackage.getCode()
        );

        if (request.getTiming() == PlanChangeTiming.IMMEDIATE && direction == PlanChangeDirection.DOWNGRADE) {
            throw new BadRequestException(DOWNGRADE_NEXT_PERIOD_ONLY);
        }

        if (request.getTiming() == PlanChangeTiming.NEXT_PERIOD) {
            return scheduleNextPeriod(user, current, fromPackage, toPackage, direction, request, toPeriod, toPeriodPrice);
        }
        return executeImmediate(
                user,
                current,
                fromPackage,
                toPackage,
                direction,
                request,
                clientIp,
                fromPeriod,
                toPeriod,
                fromPeriodPrice,
                toPeriodPrice
        );
    }

    @Transactional
    public PlanChangeResponse cancelScheduled(Long userId, Long planChangeId) {
        PlanChange planChange = planChangeRepository.findByIdForUpdate(planChangeId)
                .orElseThrow(() -> new BadRequestException("Paket gecisi bulunamadi: " + planChangeId));
        if (!planChange.getUserId().equals(userId)) {
            throw new UnauthorizedException("Bu paket gecisine erisim yetkiniz yok");
        }
        if (planChange.getStatus() != PlanChangeStatus.SCHEDULED) {
            throw new BadRequestException("Sadece planlanmis gecisler iptal edilebilir");
        }
        planChange.setStatus(PlanChangeStatus.CANCELLED);
        planChange.setCompletedAt(LocalDateTime.now());
        planChangeRepository.save(planChange);
        purchaseLogService.log(
                planChange.getFromPurchaseId(),
                userId,
                PurchaseLogAction.PLAN_CHANGE_CANCELLED,
                "Planlanmis paket gecisi iptal edildi: " + planChange.getId()
        );
        return toResponse(planChange);
    }

    @Transactional(readOnly = true)
    public List<PlanChangeResponse> listMine(Long userId) {
        return planChangeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void executeDueScheduled() {
        List<PlanChange> due = planChangeRepository.findByStatusAndEffectiveAtLessThanEqual(
                PlanChangeStatus.SCHEDULED,
                LocalDateTime.now()
        );
        for (PlanChange planChange : due) {
            try {
                executeScheduledChange(planChange.getId());
            } catch (RuntimeException exception) {
                log.error("Scheduled plan change failed. planChangeId={}", planChange.getId(), exception);
            }
        }
    }

    @Transactional
    public void executeScheduledChange(Long planChangeId) {
        PlanChange planChange = planChangeRepository.findByIdForUpdate(planChangeId)
                .orElseThrow(() -> new BadRequestException("Paket gecisi bulunamadi: " + planChangeId));
        if (planChange.getStatus() != PlanChangeStatus.SCHEDULED) {
            return;
        }
        if (planChange.getPaymentMethodId() == null) {
            failPlanChange(planChange, "Kayitli kart bulunamadi");
            return;
        }

        User user = userRepository.findById(planChange.getUserId())
                .orElseThrow(() -> new BadRequestException("Kullanici bulunamadi"));
        Purchase current = purchaseRepository.findById(planChange.getFromPurchaseId()).orElse(null);
        PlanPackage toPackage;
        try {
            toPackage = requireTargetPackage(planChange.getToPackageId(), -1L);
        } catch (BadRequestException exception) {
            failPlanChange(planChange, exception.getMessage());
            return;
        }

        planChange.setStatus(PlanChangeStatus.PENDING_PAYMENT);
        planChangeRepository.save(planChange);

        try {
            BillingPeriod toPeriod = current != null ? resolveBillingPeriod(current) : BillingPeriod.MONTHLY;
            BigDecimal toPeriodPrice = periodPrice(toPackage, toPeriod);
            Purchase newPurchase = createPendingPurchase(
                    user,
                    current,
                    toPackage,
                    planChange.getPaymentMethodId(),
                    toPeriod,
                    toPeriodPrice
            );
            planChange.setResultingPurchaseId(newPurchase.getId());
            planChange.setPaymentConversationId(newPurchase.getPaymentConversationId());
            planChange.setChargeAmount(toPeriodPrice);
            planChangeRepository.save(planChange);

            chargeDirect(
                    user,
                    newPurchase,
                    toPackage,
                    toPeriodPrice,
                    planChange.getPaymentMethodId(),
                    "127.0.0.1"
            );
            activatePurchaseNow(newPurchase, toPackage, current, true);
            bootstrapSubscriptionAfterPlanChange(user, newPurchase, toPackage, planChange.getPaymentMethodId());
            completePlanChange(planChange, newPurchase, current);
        } catch (RuntimeException exception) {
            compensateFailedPlanChange(planChange, user, "127.0.0.1");
            failPlanChange(planChange, exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public void onPurchaseActivated(Purchase purchase) {
        planChangeRepository.findByResultingPurchaseIdForUpdate(purchase.getId()).ifPresent(planChange -> {
            if (planChange.getStatus() == PlanChangeStatus.COMPLETED
                    || planChange.getStatus() == PlanChangeStatus.CANCELLED) {
                return;
            }
            Purchase fromPurchase = purchaseRepository.findById(planChange.getFromPurchaseId()).orElse(null);
            completePlanChange(planChange, purchase, fromPurchase);
        });
    }

    @Transactional
    public void onPurchasePaymentFailed(Purchase purchase) {
        planChangeRepository.findByResultingPurchaseIdForUpdate(purchase.getId()).ifPresent(planChange -> {
            if (planChange.getStatus() == PlanChangeStatus.PENDING_PAYMENT) {
                failPlanChange(planChange, "Odeme basarisiz");
            }
        });
    }

    @Transactional
    public void cancelScheduledForUser(Long userId) {
        planChangeRepository.findByUserIdAndStatus(userId, PlanChangeStatus.SCHEDULED).ifPresent(planChange -> {
            planChange.setStatus(PlanChangeStatus.CANCELLED);
            planChange.setCompletedAt(LocalDateTime.now());
            planChangeRepository.save(planChange);
            purchaseLogService.log(
                    planChange.getFromPurchaseId(),
                    userId,
                    PurchaseLogAction.PLAN_CHANGE_CANCELLED,
                    "Paket gecisi kullanici iptali nedeniyle iptal edildi"
            );
        });
    }

    private PlanChangeResponse scheduleNextPeriod(
            User user,
            Purchase current,
            PlanPackage fromPackage,
            PlanPackage toPackage,
            PlanChangeDirection direction,
            PlanChangeRequest request,
            BillingPeriod toPeriod,
            BigDecimal toPeriodPrice
    ) {
        if (current.getExpiresAt() == null) {
            throw new BadRequestException("Mevcut paketin bitis tarihi yok");
        }
        Long paymentMethodId = request.getPaymentMethodId() != null
                ? request.getPaymentMethodId()
                : current.getPaymentMethodId();
        if (paymentMethodId == null) {
            throw new BadRequestException("Donem sonu gecisi icin kayitli kart zorunludur");
        }
        ensurePaymentMethodExists(user.getId(), paymentMethodId);

        PlanChange planChange = planChangeRepository.save(PlanChange.builder()
                .userId(user.getId())
                .fromPurchaseId(current.getId())
                .fromPackageId(fromPackage.getId())
                .toPackageId(toPackage.getId())
                .direction(direction)
                .timing(PlanChangeTiming.NEXT_PERIOD)
                .status(PlanChangeStatus.SCHEDULED)
                .chargeAmount(toPeriodPrice)
                .refundAmount(BigDecimal.ZERO)
                .currency(toPackage.getCurrency())
                .paymentMethodId(paymentMethodId)
                .effectiveAt(current.getExpiresAt())
                .warningAck(true)
                .build());

        purchaseLogService.log(
                current.getId(),
                user.getId(),
                PurchaseLogAction.PLAN_CHANGE_SCHEDULED,
                toPackage.getCode() + " paketine gecis planlandi: " + planChange.getEffectiveAt()
                        + " (" + toPeriod.name() + ")"
        );
        return toResponse(planChange);
    }

    private PlanChangeResponse executeImmediate(
            User user,
            Purchase current,
            PlanPackage fromPackage,
            PlanPackage toPackage,
            PlanChangeDirection direction,
            PlanChangeRequest request,
            String clientIp,
            BillingPeriod fromPeriod,
            BillingPeriod toPeriod,
            BigDecimal fromPeriodPrice,
            BigDecimal toPeriodPrice
    ) {
        double fraction = remainingFraction(current, LocalDateTime.now());
        MoneyDelta delta;
        boolean resetAnchor = fromPeriod == BillingPeriod.MONTHLY && toPeriod == BillingPeriod.YEARLY;
        if (resetAnchor) {
            BigDecimal credit = fromPeriodPrice.multiply(BigDecimal.valueOf(fraction))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal chargeNow = toPeriodPrice.subtract(credit).max(BigDecimal.ZERO);
            delta = new MoneyDelta(chargeNow, BigDecimal.ZERO);
        } else {
            delta = resolveProratedUpgradeDelta(fromPeriodPrice, toPeriodPrice, fraction);
        }
        if (delta.chargeAmount().signum() > 0) {
            if (request.getPaymentMethodId() == null) {
                throw new BadRequestException("Fark odemesi icin kayitli kart zorunludur");
            }
            ensurePaymentMethodExists(user.getId(), request.getPaymentMethodId());
        }
        Long paymentMethodId = request.getPaymentMethodId() != null
                ? request.getPaymentMethodId()
                : current.getPaymentMethodId();
        if (paymentMethodId == null) {
            throw new BadRequestException("Abonelik icin kayitli kart zorunludur");
        }

        PlanChange planChange = planChangeRepository.save(PlanChange.builder()
                .userId(user.getId())
                .fromPurchaseId(current.getId())
                .fromPackageId(fromPackage.getId())
                .toPackageId(toPackage.getId())
                .direction(direction)
                .timing(PlanChangeTiming.IMMEDIATE)
                .status(PlanChangeStatus.PENDING_PAYMENT)
                .chargeAmount(delta.chargeAmount())
                .refundAmount(BigDecimal.ZERO)
                .currency(toPackage.getCurrency())
                .paymentMethodId(paymentMethodId)
                .effectiveAt(LocalDateTime.now())
                .warningAck(true)
                .build());

        try {
            Purchase newPurchase = createPendingPurchase(
                    user,
                    current,
                    toPackage,
                    paymentMethodId,
                    toPeriod,
                    toPeriodPrice
            );
            planChange.setResultingPurchaseId(newPurchase.getId());
            planChange.setPaymentConversationId(newPurchase.getPaymentConversationId());
            planChangeRepository.save(planChange);

            if (delta.chargeAmount().signum() > 0) {
                purchaseLogService.log(
                        newPurchase.getId(),
                        user.getId(),
                        PurchaseLogAction.PLAN_CHANGE_PAYMENT_STARTED,
                        toPackage.getCode() + " paket gecisi icin fark odemesi baslatildi: "
                                + delta.chargeAmount()
                );
                chargeDirect(
                        user,
                        newPurchase,
                        toPackage,
                        delta.chargeAmount(),
                        paymentMethodId,
                        clientIp
                );
            }

            activatePurchaseNow(newPurchase, toPackage, current, resetAnchor);
            bootstrapSubscriptionAfterPlanChange(user, newPurchase, toPackage, paymentMethodId);
            completePlanChange(planChange, newPurchase, current);
            return toResponse(planChangeRepository.findById(planChange.getId()).orElseThrow());
        } catch (RuntimeException exception) {
            compensateFailedPlanChange(planChange, user, clientIp);
            failPlanChange(planChange, exception.getMessage());
            throw exception;
        }
    }

    private void bootstrapSubscriptionAfterPlanChange(
            User user,
            Purchase purchase,
            PlanPackage planPackage,
            Long paymentMethodId
    ) {
        if (paymentMethodId == null) {
            paymentMethodId = purchase.getPaymentMethodId();
        }
        if (paymentMethodId == null) {
            throw new BadRequestException("Abonelik icin kayitli kart zorunludur");
        }
        Map<String, Object> sourceMetadata = new HashMap<>();
        sourceMetadata.put("userId", user.getId());
        sourceMetadata.put("packageId", planPackage.getId());
        sourceMetadata.put("packageCode", planPackage.getCode());
        sourceMetadata.put("purchaseId", purchase.getId());
        sourceMetadata.put("purchaseConversationId", purchase.getPaymentConversationId());
        sourceMetadata.put("paymentStyle", PaymentStyle.SUBSCRIPTION.name());
        sourceMetadata.put("billingPeriod", purchase.getBillingPeriod() == null
                ? null
                : purchase.getBillingPeriod().name());
        sourceMetadata.put("planChangeBootstrap", true);

        var subscription = paymentServiceClient.bootstrapSubscription(
                user.getId(),
                appProperties.getServiceName(),
                String.valueOf(purchase.getId()),
                purchase.getPaymentConversationId(),
                purchase.getPrice(),
                purchase.getCurrency(),
                purchase.getBillingIntervalMonths() == null ? 1 : purchase.getBillingIntervalMonths(),
                paymentMethodId,
                purchase.getExpiresAt(),
                sourceMetadata
        );
        if (subscription != null && subscription.id() != null) {
            purchase.setSubscriptionId(subscription.id());
            purchase.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            purchaseRepository.save(purchase);
        }
    }

    private void refundDifference(
            User user,
            Purchase fromPurchase,
            PlanChange planChange,
            BigDecimal refundAmount,
            String clientIp
    ) {
        String conversationId = fromPurchase.getPaymentConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            throw new BadRequestException("Onceki odeme kaydi bulunamadi; iade yapilamiyor");
        }
        purchaseLogService.log(
                fromPurchase.getId(),
                user.getId(),
                PurchaseLogAction.PLAN_CHANGE_REFUND_STARTED,
                "Paket dusurme iadesi baslatildi: " + refundAmount
        );
        try {
            paymentServiceClient.refundPayment(user.getId(), conversationId, refundAmount, clientIp);
            purchaseLogService.log(
                    fromPurchase.getId(),
                    user.getId(),
                    PurchaseLogAction.PLAN_CHANGE_REFUND_COMPLETED,
                    "Paket dusurme iadesi tamamlandi: " + refundAmount
            );
            planChange.setRefundAmount(refundAmount);
            planChangeRepository.save(planChange);
        } catch (PaymentServiceException exception) {
            purchaseLogService.log(
                    fromPurchase.getId(),
                    user.getId(),
                    PurchaseLogAction.PLAN_CHANGE_PAYMENT_FAILED,
                    "Paket dusurme iadesi basarisiz: " + exception.getMessage()
            );
            throw exception;
        }
    }

    private Purchase createPendingPurchase(
            User user,
            Purchase current,
            PlanPackage toPackage,
            Long paymentMethodId,
            BillingPeriod billingPeriod,
            BigDecimal periodPrice
    ) {
        CardSnapshot cardSnapshot = resolveCardSnapshot(user.getId(), paymentMethodId);

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .userId(user.getId())
                .packageId(toPackage.getId())
                .packageCode(toPackage.getCode())
                .packageName(toPackage.getName())
                .price(periodPrice)
                .currency(toPackage.getCurrency())
                .paymentMode(PaymentMode.DIRECT)
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(billingPeriod)
                .billingIntervalMonths(billingPeriod.intervalMonths())
                .purchaseType(PurchaseType.PAID)
                .installmentCount(1)
                .paymentMethodId(paymentMethodId)
                .cardBrand(cardSnapshot.brand())
                .cardLastFour(cardSnapshot.lastFour())
                .billingSnapshot(current != null ? current.getBillingSnapshot() : null)
                .status(PurchaseStatus.PENDING)
                .build());

        purchase.setPaymentConversationId(paymentRequestMapper.buildConversationId(purchase.getId()));
        purchaseRepository.save(purchase);
        purchaseFulfillmentService.initializeSchedule(purchase, appProperties.getServiceName());
        return purchase;
    }

    private void chargeDirect(
            User user,
            Purchase purchase,
            PlanPackage planPackage,
            BigDecimal chargeAmount,
            Long paymentMethodId,
            String clientIp
    ) {
        if (purchase.getBillingSnapshot() == null) {
            throw new BadRequestException("Fatura bilgisi bulunamadi; once fatura adresi tanimlayin");
        }
        BigDecimal amount = chargeAmount == null ? BigDecimal.ZERO : chargeAmount;
        if (amount.signum() <= 0) {
            return;
        }
        Map<String, Object> sourceMetadata = new HashMap<>();
        sourceMetadata.put("userId", user.getId());
        sourceMetadata.put("packageId", planPackage.getId());
        sourceMetadata.put("packageCode", planPackage.getCode());
        sourceMetadata.put("purchaseConversationId", purchase.getPaymentConversationId());
        sourceMetadata.put("purchaseId", purchase.getId());
        sourceMetadata.put("installmentNumber", 1);
        sourceMetadata.put("installmentCount", 1);
        sourceMetadata.put("paymentStyle", PaymentStyle.ONE_TIME.name());
        sourceMetadata.put("validityDays", planPackage.getValidityDays());
        sourceMetadata.put("totalAmount", amount);
        sourceMetadata.put("planChange", true);
        sourceMetadata.put("planChangeDifference", true);

        PaymentThreeDsRequest paymentRequest = PaymentThreeDsRequest.builder()
                .serviceName(appProperties.getServiceName())
                .sourceReferenceId(String.valueOf(purchase.getId()))
                .sourceMetadata(sourceMetadata)
                .conversationId(purchase.getPaymentConversationId())
                .locale("tr")
                .price(amount)
                .paidPrice(amount)
                .currency(planPackage.getCurrency())
                .paymentMode(PaymentMode.DIRECT.name())
                .paymentStyle(PaymentStyle.ONE_TIME.name())
                .installmentCount(1)
                .subscriptionCycleCount(null)
                .billingIntervalMonths(null)
                .installment(1)
                .basketId("qr-plan-change-" + purchase.getId())
                .paymentChannel("WEB")
                .paymentGroup("PRODUCT")
                .paymentMethodId(paymentMethodId)
                .buyer(toBuyer(user, purchase, clientIp))
                .shippingAddress(toAddress(purchase))
                .billingAddress(toAddress(purchase))
                .basketItems(List.of(PaymentThreeDsRequest.BasketItemPayload.builder()
                        .id(String.valueOf(planPackage.getId()))
                        .name(planPackage.getName() + " (fark)")
                        .category1("Digital")
                        .category2("Package")
                        .itemType("VIRTUAL")
                        .price(amount)
                        .build()))
                .build();

        try {
            PaymentThreeDsResponse response = paymentServiceClient.createDirectPayment(paymentRequest);
            if (response != null && response.getConversationId() != null) {
                purchase.setPaymentConversationId(response.getConversationId());
                purchaseRepository.save(purchase);
            }
        } catch (PaymentServiceException exception) {
            purchase.setStatus(PurchaseStatus.FAILED);
            purchaseRepository.save(purchase);
            purchaseLogService.log(
                    purchase.getId(),
                    user.getId(),
                    PurchaseLogAction.PLAN_CHANGE_PAYMENT_FAILED,
                    "Paket gecisi odemesi basarisiz: " + exception.getMessage()
            );
            throw exception;
        }
    }

    private void activatePurchaseNow(
            Purchase purchase,
            PlanPackage planPackage,
            Purchase fromPurchase,
            boolean resetAnchor
    ) {
        LocalDateTime now = LocalDateTime.now();
        purchase.setStartsAt(now);
        if (!resetAnchor && fromPurchase != null && fromPurchase.getExpiresAt() != null) {
            purchase.setExpiresAt(fromPurchase.getExpiresAt());
        } else {
            int months = purchase.getBillingIntervalMonths() == null ? 1 : purchase.getBillingIntervalMonths();
            purchase.setExpiresAt(now.plusMonths(months));
        }
        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchaseRepository.save(purchase);

        for (PlanPackageItem item : planPackage.getItems()) {
            entitlementService.grant(
                    purchase,
                    item.getProduct().getId(),
                    item.getProduct().getCode(),
                    item.getQuantity(),
                    item.isUnlimited()
            );
        }
        packageActivationService.activatePurchasedPackage(purchase);
        menuPublicAccessService.syncForUser(purchase.getUserId());
    }

    private void completePlanChange(PlanChange planChange, Purchase newPurchase, Purchase fromPurchase) {
        if (fromPurchase != null) {
            resetPreviousEntitlements(fromPurchase, newPurchase);
            cancelPreviousSubscription(fromPurchase);
            if (fromPurchase.getStatus() == PurchaseStatus.ACTIVE
                    && !fromPurchase.getId().equals(newPurchase.getId())) {
                fromPurchase.setStatus(PurchaseStatus.SUPERSEDED);
                purchaseRepository.save(fromPurchase);
            }
        }

        planChange.setStatus(PlanChangeStatus.COMPLETED);
        planChange.setResultingPurchaseId(newPurchase.getId());
        planChange.setCompletedAt(LocalDateTime.now());
        planChange.setPaymentId(newPurchase.getPaymentId());
        planChangeRepository.save(planChange);

        purchaseLogService.log(
                newPurchase.getId(),
                planChange.getUserId(),
                PurchaseLogAction.PLAN_CHANGE_COMPLETED,
                planChange.getDirection() + " tamamlandi: purchase=" + newPurchase.getId()
                        + " timing=" + planChange.getTiming()
        );
    }

    private void resetPreviousEntitlements(Purchase fromPurchase, Purchase newPurchase) {
        if (fromPurchase.getId().equals(newPurchase.getId())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (fromPurchase.getExpiresAt() == null || fromPurchase.getExpiresAt().isAfter(now)) {
            fromPurchase.setExpiresAt(now);
            purchaseRepository.save(fromPurchase);
        }
        entitlementService.revokeForCancelledPurchase(fromPurchase);
        purchaseLogService.log(
                fromPurchase.getId(),
                fromPurchase.getUserId(),
                PurchaseLogAction.PLAN_CHANGE_ENTITLEMENTS_RESET,
                "Eski paket haklari sifirlandi; yeni paket haklari tanimlandi"
        );
    }

    private void cancelPreviousSubscription(Purchase fromPurchase) {
        if (fromPurchase.getPaymentStyle() != PaymentStyle.SUBSCRIPTION) {
            return;
        }
        String subscriptionId = fromPurchase.getSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return;
        }
        try {
            paymentServiceClient.cancelSubscription(fromPurchase.getUserId(), subscriptionId);
            fromPurchase.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
            purchaseRepository.save(fromPurchase);
        } catch (PaymentServiceException exception) {
            log.error(
                    "Eski abonelik iptal edilemedi. purchaseId={} subscriptionId={}",
                    fromPurchase.getId(),
                    subscriptionId,
                    exception
            );
            throw new BadRequestException("Onceki abonelik iptal edilemedi; paket gecisi guvenli tamamlanamadi");
        }
    }

    private void failPlanChange(PlanChange planChange, String reason) {
        planChange.setStatus(PlanChangeStatus.FAILED);
        planChange.setCompletedAt(LocalDateTime.now());
        planChangeRepository.save(planChange);
        purchaseLogService.log(
                planChange.getFromPurchaseId(),
                planChange.getUserId(),
                PurchaseLogAction.PLAN_CHANGE_PAYMENT_FAILED,
                "Paket gecisi basarisiz: " + (reason == null ? "bilinmeyen hata" : reason)
        );
    }

    private void compensateFailedPlanChange(PlanChange planChange, User user, String clientIp) {
        if (planChange.getResultingPurchaseId() == null) {
            return;
        }
        Purchase newPurchase = purchaseRepository.findById(planChange.getResultingPurchaseId()).orElse(null);
        if (newPurchase == null) {
            return;
        }

        BigDecimal chargeAmount = planChange.getChargeAmount();
        if (chargeAmount != null && chargeAmount.signum() > 0) {
            String conversationId = planChange.getPaymentConversationId() != null
                    ? planChange.getPaymentConversationId()
                    : newPurchase.getPaymentConversationId();
            if (conversationId != null && !conversationId.isBlank()) {
                try {
                    BigDecimal remaining = resolveRefundableRemaining(user.getId(), conversationId);
                    if (remaining.signum() > 0) {
                        paymentServiceClient.refundPayment(
                                user.getId(),
                                conversationId,
                                remaining.min(chargeAmount),
                                clientIp
                        );
                    }
                } catch (RuntimeException exception) {
                    log.error(
                            "Plan-change compensation refund failed. planChangeId={} conversationId={}",
                            planChange.getId(),
                            conversationId,
                            exception
                    );
                }
            }
        }

        if (newPurchase.getStatus() == PurchaseStatus.ACTIVE
                || newPurchase.getStatus() == PurchaseStatus.PENDING) {
            if (newPurchase.getStatus() == PurchaseStatus.ACTIVE) {
                entitlementService.revokeForCancelledPurchase(newPurchase);
            }
            newPurchase.setStatus(PurchaseStatus.FAILED);
            newPurchase.setExpiresAt(LocalDateTime.now());
            if (newPurchase.getSubscriptionId() != null && !newPurchase.getSubscriptionId().isBlank()) {
                try {
                    paymentServiceClient.cancelSubscription(user.getId(), newPurchase.getSubscriptionId());
                    newPurchase.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
                } catch (RuntimeException exception) {
                    log.warn(
                            "Plan-change compensation subscription cancel failed. purchaseId={}",
                            newPurchase.getId()
                    );
                }
            }
            purchaseRepository.save(newPurchase);
            menuPublicAccessService.syncForUser(user.getId());
        }
    }

    private Purchase requireUsablePaidPurchase(Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);
        List<Purchase> usable = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .filter(purchase -> purchase.getPurchaseType() == PurchaseType.PAID
                        || purchase.getPurchaseType() == PurchaseType.TRIAL)
                .filter(purchase -> !CatalogPackages.FREE_PACKAGE.equals(purchase.getPackageCode()))
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.FREE)
                .toList();
        List<Purchase> paidOnly = usable.stream()
                .filter(purchase -> purchase.getPurchaseType() == PurchaseType.PAID)
                .toList();
        if (paidOnly.isEmpty()) {
            throw new BadRequestException("Paket gecisi icin aktif ucretli paket gerekli");
        }
        return paidOnly.getFirst();
    }

    private PlanPackage requireTargetPackage(Long toPackageId, Long fromPackageId) {
        PlanPackage toPackage = planPackageService.findActivePackage(toPackageId);
        if (!toPackage.isPurchasable() || toPackage.isSystemManaged()
                || CatalogPackages.FREE_PACKAGE.equals(toPackage.getCode())) {
            throw new BadRequestException("Bu paket gecis hedefi olamaz");
        }
        if (toPackage.getId().equals(fromPackageId)) {
            throw new BadRequestException("Ayni pakete gecis yapilamaz");
        }
        if (toPackage.effectiveMonthlyPrice() == null || toPackage.effectiveMonthlyPrice().signum() <= 0) {
            throw new BadRequestException("Hedef paket fiyati gecersiz");
        }
        return toPackage;
    }

    private BillingPeriod resolveBillingPeriod(Purchase purchase) {
        if (purchase.getBillingPeriod() != null) {
            return purchase.getBillingPeriod();
        }
        if (purchase.getBillingIntervalMonths() != null && purchase.getBillingIntervalMonths() >= 12) {
            return BillingPeriod.YEARLY;
        }
        return BillingPeriod.MONTHLY;
    }

    private BigDecimal periodPrice(PlanPackage planPackage, BillingPeriod period) {
        BigDecimal price = period == BillingPeriod.YEARLY
                ? planPackage.effectiveYearlyPrice()
                : planPackage.effectiveMonthlyPrice();
        if (price == null || price.signum() <= 0) {
            throw new BadRequestException("Paket donem fiyati gecersiz");
        }
        return price;
    }

    private double remainingFraction(Purchase purchase, LocalDateTime now) {
        LocalDateTime periodEnd = purchase.getExpiresAt();
        LocalDateTime periodStart = purchase.getStartsAt();
        if (periodEnd == null || periodStart == null || !periodEnd.isAfter(periodStart)) {
            return 1.0d;
        }
        if (!periodEnd.isAfter(now)) {
            return 0.0d;
        }
        long totalDays = Math.max(ChronoUnit.DAYS.between(periodStart, periodEnd), 1);
        long remainingDays = Math.max(ChronoUnit.DAYS.between(now, periodEnd), 0);
        double fraction = (double) remainingDays / (double) totalDays;
        if (fraction < 0) {
            return 0.0d;
        }
        if (fraction > 1) {
            return 1.0d;
        }
        return fraction;
    }

    private MoneyDelta resolveProratedUpgradeDelta(
            BigDecimal fromPeriodPrice,
            BigDecimal toPeriodPrice,
            double remainingFraction
    ) {
        BigDecimal from = fromPeriodPrice == null ? BigDecimal.ZERO : fromPeriodPrice;
        BigDecimal to = toPeriodPrice == null ? BigDecimal.ZERO : toPeriodPrice;
        BigDecimal delta = to.subtract(from);
        if (delta.signum() <= 0) {
            return new MoneyDelta(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal charge = delta.multiply(BigDecimal.valueOf(remainingFraction))
                .setScale(2, RoundingMode.HALF_UP);
        return new MoneyDelta(charge.max(BigDecimal.ZERO), BigDecimal.ZERO);
    }

    private PlanChangeDirection resolveDirection(BigDecimal fromPrice, BigDecimal toPrice) {
        int cmp = toPrice.compareTo(fromPrice);
        if (cmp > 0) {
            return PlanChangeDirection.UPGRADE;
        }
        if (cmp < 0) {
            return PlanChangeDirection.DOWNGRADE;
        }
        return PlanChangeDirection.LATERAL;
    }

    private void ensurePaymentMethodExists(Long userId, Long paymentMethodId) {
        boolean exists = paymentServiceClient.getPaymentMethods(userId).stream()
                .anyMatch(method -> String.valueOf(paymentMethodId).equals(method.id()));
        if (!exists) {
            throw new BadRequestException("Kayitli kart bulunamadi");
        }
    }

    private CardSnapshot resolveCardSnapshot(Long userId, Long paymentMethodId) {
        try {
            return paymentServiceClient.getPaymentMethods(userId).stream()
                    .filter(method -> String.valueOf(paymentMethodId).equals(method.id()))
                    .findFirst()
                    .map(method -> new CardSnapshot(trimToNull(method.brand()), trimToNull(method.lastFour())))
                    .orElse(new CardSnapshot(null, null));
        } catch (RuntimeException exception) {
            log.warn("Kart snapshot alinamadi. userId={} paymentMethodId={}", userId, paymentMethodId, exception);
            return new CardSnapshot(null, null);
        }
    }

    private PaymentThreeDsRequest.BuyerPayload toBuyer(User user, Purchase purchase, String clientIp) {
        var address = purchase.getBillingSnapshot();
        String identity = address.getTckn() != null ? address.getTckn() : address.getVkn();
        return PaymentThreeDsRequest.BuyerPayload.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getFirstName())
                .surname(user.getLastName())
                .gsmNumber(user.getPhone())
                .email(user.getEmail())
                .identityNumber(identity != null ? identity : "11111111111")
                .registrationAddress(address.getAddress())
                .ip(clientIp != null && !clientIp.isBlank() ? clientIp : "127.0.0.1")
                .city(address.getCity())
                .country(address.getCountry())
                .zipCode(address.getPostcode())
                .build();
    }

    private PaymentThreeDsRequest.AddressPayload toAddress(Purchase purchase) {
        var address = purchase.getBillingSnapshot();
        return PaymentThreeDsRequest.AddressPayload.builder()
                .contactName(address.getLegalName() != null
                        ? address.getLegalName()
                        : String.join(" ", nullToEmpty(address.getName()), nullToEmpty(address.getSurname())).trim())
                .city(address.getCity())
                .country(address.getCountry())
                .address(address.getAddress())
                .zipCode(address.getPostcode())
                .build();
    }

    private PlanChangePackageSummary toSummary(PlanPackage planPackage) {
        return PlanChangePackageSummary.builder()
                .id(planPackage.getId())
                .code(planPackage.getCode())
                .name(planPackage.getName())
                .price(planPackage.getPrice())
                .monthlyDiscount(planPackage.getMonthlyDiscount())
                .yearlyPrice(planPackage.getYearlyPrice())
                .yearlyDiscount(planPackage.getYearlyDiscount())
                .effectiveMonthlyPrice(planPackage.effectiveMonthlyPrice())
                .effectiveYearlyPrice(planPackage.effectiveYearlyPrice())
                .currency(planPackage.getCurrency())
                .validityDays(planPackage.getValidityDays())
                .features(planPackage.getFeatures() == null ? List.of() : List.copyOf(planPackage.getFeatures()))
                .build();
    }

    private PlanChangeResponse toResponse(PlanChange planChange) {
        PlanPackage from = planPackageRepository.findById(planChange.getFromPackageId()).orElse(null);
        PlanPackage to = planPackageRepository.findById(planChange.getToPackageId()).orElse(null);
        return PlanChangeResponse.builder()
                .id(planChange.getId())
                .userId(planChange.getUserId())
                .fromPurchaseId(planChange.getFromPurchaseId())
                .fromPackageId(planChange.getFromPackageId())
                .toPackageId(planChange.getToPackageId())
                .fromPackageCode(from != null ? from.getCode() : null)
                .toPackageCode(to != null ? to.getCode() : null)
                .fromPackageName(from != null ? from.getName() : null)
                .toPackageName(to != null ? to.getName() : null)
                .direction(planChange.getDirection())
                .timing(planChange.getTiming())
                .status(planChange.getStatus())
                .chargeAmount(planChange.getChargeAmount())
                .refundAmount(planChange.getRefundAmount())
                .currency(planChange.getCurrency())
                .paymentMethodId(planChange.getPaymentMethodId())
                .effectiveAt(planChange.getEffectiveAt())
                .resultingPurchaseId(planChange.getResultingPurchaseId())
                .warningAck(planChange.isWarningAck())
                .createdAt(planChange.getCreatedAt())
                .completedAt(planChange.getCompletedAt())
                .build();
    }

    private MoneyDelta resolveImmediateMoneyDelta(Long userId, Purchase current, PlanPackage toPackage) {
        MoneyDelta catalogDelta = resolveMoneyDelta(current.getPrice(), toPackage.getPrice());
        if (catalogDelta.refundAmount().signum() <= 0) {
            return catalogDelta;
        }
        BigDecimal remaining = resolveRefundableRemaining(userId, current.getPaymentConversationId());
        BigDecimal refundNow = catalogDelta.refundAmount().min(remaining).max(BigDecimal.ZERO);
        return new MoneyDelta(catalogDelta.chargeAmount(), refundNow);
    }

    private BigDecimal resolveRefundableRemaining(Long userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return paymentServiceClient.getRefundablePayment(userId, conversationId).remaining();
        } catch (PaymentServiceException exception) {
            log.warn(
                    "Refundable balance unavailable; treating remaining as zero. conversationId={} reason={}",
                    conversationId,
                    exception.getMessage()
            );
            return BigDecimal.ZERO;
        }
    }

    private MoneyDelta resolveMoneyDelta(BigDecimal fromPaidPrice, BigDecimal toPackagePrice) {
        BigDecimal from = fromPaidPrice == null ? BigDecimal.ZERO : fromPaidPrice;
        BigDecimal to = toPackagePrice == null ? BigDecimal.ZERO : toPackagePrice;
        BigDecimal charge = to.subtract(from).max(BigDecimal.ZERO);
        BigDecimal refund = from.subtract(to).max(BigDecimal.ZERO);
        return new MoneyDelta(charge, refund);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CardSnapshot(String brand, String lastFour) {
    }

    private record MoneyDelta(BigDecimal chargeAmount, BigDecimal refundAmount) {
    }
}
