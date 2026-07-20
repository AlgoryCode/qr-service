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
import java.time.LocalDateTime;
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

    @Transactional(readOnly = true)
    public PlanChangePreviewResponse preview(Long userId, Long toPackageId) {
        Purchase current = requireUsablePaidPurchase(userId);
        PlanPackage fromPackage = planPackageService.findPackage(current.getPackageId());
        PlanPackage toPackage = requireTargetPackage(toPackageId, current.getPackageId());
        PlanChangeDirection direction = resolveDirection(fromPackage.getPrice(), toPackage.getPrice());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodEnd = current.getExpiresAt();

        MoneyDelta catalogDelta = resolveMoneyDelta(current.getPrice(), toPackage.getPrice());
        MoneyDelta immediateDelta = resolveImmediateMoneyDelta(userId, current, toPackage);
        List<String> warnings = new ArrayList<>();
        warnings.add(NO_CARRYOVER_WARNING);
        if (catalogDelta.refundAmount().compareTo(immediateDelta.refundAmount()) > 0) {
            warnings.add(REFUND_CLAMPED_WARNING);
        }
        List<PlanChangeOptionResponse> options = List.of(
                PlanChangeOptionResponse.builder()
                        .timing(PlanChangeTiming.IMMEDIATE)
                        .chargeNow(immediateDelta.chargeAmount())
                        .refundNow(immediateDelta.refundAmount())
                        .chargeAtEffective(BigDecimal.ZERO)
                        .effectiveAt(now)
                        .entitlementsPolicy(ENTITLEMENTS_POLICY)
                        .build(),
                PlanChangeOptionResponse.builder()
                        .timing(PlanChangeTiming.NEXT_PERIOD)
                        .chargeNow(BigDecimal.ZERO)
                        .refundNow(BigDecimal.ZERO)
                        .chargeAtEffective(toPackage.getPrice())
                        .effectiveAt(periodEnd)
                        .entitlementsPolicy(ENTITLEMENTS_POLICY)
                        .build()
        );

        return PlanChangePreviewResponse.builder()
                .fromPurchaseId(current.getId())
                .fromPackage(toSummary(fromPackage))
                .toPackage(toSummary(toPackage))
                .direction(direction)
                .currentExpiresAt(periodEnd)
                .options(options)
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
        PlanChangeDirection direction = resolveDirection(fromPackage.getPrice(), toPackage.getPrice());

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

        if (request.getTiming() == PlanChangeTiming.NEXT_PERIOD) {
            return scheduleNextPeriod(user, current, fromPackage, toPackage, direction, request);
        }
        return executeImmediate(user, current, fromPackage, toPackage, direction, request, clientIp);
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
            Purchase newPurchase = createPendingPurchase(
                    user,
                    current,
                    toPackage,
                    planChange.getPaymentMethodId()
            );
            planChange.setResultingPurchaseId(newPurchase.getId());
            planChange.setPaymentConversationId(newPurchase.getPaymentConversationId());
            planChange.setChargeAmount(toPackage.getPrice());
            planChangeRepository.save(planChange);

            chargeDirect(
                    user,
                    newPurchase,
                    toPackage,
                    toPackage.getPrice(),
                    planChange.getPaymentMethodId(),
                    "127.0.0.1"
            );
            activatePurchaseNow(newPurchase, toPackage);
            completePlanChange(planChange, newPurchase, current);
        } catch (RuntimeException exception) {
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
            PlanChangeRequest request
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
                .chargeAmount(toPackage.getPrice())
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
            String clientIp
    ) {
        MoneyDelta delta = resolveImmediateMoneyDelta(user.getId(), current, toPackage);
        if (delta.chargeAmount().signum() > 0) {
            if (request.getPaymentMethodId() == null) {
                throw new BadRequestException("Fark odemesi icin kayitli kart zorunludur");
            }
            ensurePaymentMethodExists(user.getId(), request.getPaymentMethodId());
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
                .refundAmount(delta.refundAmount())
                .currency(toPackage.getCurrency())
                .paymentMethodId(request.getPaymentMethodId())
                .effectiveAt(LocalDateTime.now())
                .warningAck(true)
                .build());

        try {
            if (delta.refundAmount().signum() > 0) {
                refundDifference(user, current, planChange, delta.refundAmount(), clientIp);
            }

            Purchase newPurchase = createPendingPurchase(
                    user,
                    current,
                    toPackage,
                    request.getPaymentMethodId()
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
                        request.getPaymentMethodId(),
                        clientIp
                );
            }

            activatePurchaseNow(newPurchase, toPackage);
            completePlanChange(planChange, newPurchase, current);
            return toResponse(planChangeRepository.findById(planChange.getId()).orElseThrow());
        } catch (RuntimeException exception) {
            failPlanChange(planChange, exception.getMessage());
            throw exception;
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
            Long paymentMethodId
    ) {
        PaymentStyle paymentStyle = current != null && current.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                ? PaymentStyle.SUBSCRIPTION
                : PaymentStyle.ONE_TIME;
        CardSnapshot cardSnapshot = resolveCardSnapshot(user.getId(), paymentMethodId);

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .userId(user.getId())
                .packageId(toPackage.getId())
                .packageCode(toPackage.getCode())
                .packageName(toPackage.getName())
                .price(toPackage.getPrice())
                .currency(toPackage.getCurrency())
                .paymentMode(PaymentMode.DIRECT)
                .paymentStyle(paymentStyle)
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
        if (paymentStyle == PaymentStyle.SUBSCRIPTION) {
            purchaseFulfillmentService.initializeSchedule(purchase, appProperties.getServiceName());
        }
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
        sourceMetadata.put("installmentNumber", 1);
        sourceMetadata.put("installmentCount", 1);
        sourceMetadata.put("bankInstallmentCount", 1);
        sourceMetadata.put("paymentStyle", purchase.getPaymentStyle().name());
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
                .paymentStyle(purchase.getPaymentStyle().name())
                .installmentCount(1)
                .subscriptionCycleCount(purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION ? 12 : null)
                .billingIntervalMonths(purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION ? 1 : null)
                .installment(1)
                .basketId("qr-plan-change-" + purchase.getId())
                .paymentChannel("WEB")
                .paymentGroup(purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION ? "SUBSCRIPTION" : "PRODUCT")
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

    private void activatePurchaseNow(Purchase purchase, PlanPackage planPackage) {
        LocalDateTime now = LocalDateTime.now();
        purchase.setStartsAt(now);
        purchase.setExpiresAt(now.plusDays(planPackage.getValidityDays()));
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
        if (toPackage.getPrice() == null || toPackage.getPrice().signum() <= 0) {
            throw new BadRequestException("Hedef paket fiyati gecersiz");
        }
        return toPackage;
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
