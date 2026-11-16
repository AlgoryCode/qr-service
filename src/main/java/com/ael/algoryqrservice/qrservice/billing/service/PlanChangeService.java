package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionKind;
import com.ael.algoryqrservice.qrservice.billing.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.qrservice.billing.dto.PaymentEventMetadata;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangeOptionResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangePackageSummary;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangePreviewResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangeRequest;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangeResponse;
import com.ael.algoryqrservice.qrservice.billing.paymentrequestmapper.service.PaymentRequestMapper;
import com.ael.algoryqrservice.qrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.qrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.qrservice.config.AppProperties;
import com.ael.algoryqrservice.qrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.qrservice.model.PlanChange;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PlanChangeDirection;
import com.ael.algoryqrservice.qrservice.model.enums.PlanChangeStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PlanChangeTiming;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.qrservice.repository.PlanChangeRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.BillingPeriodResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package upgrades and downgrades for a merchant on a paid package.
 *
 * <ul>
 *   <li>IMMEDIATE (upgrades only): a new purchase for the target package is paid for the prorated
 *   difference, runs until the current billing date and takes over the subscription, whose renewals
 *   then charge the target package's price.</li>
 *   <li>NEXT_PERIOD: a scheduled change; at period end the current purchase and its subscription are
 *   switched to the target package, so the renewal charges and fulfils the target package.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanChangeService {

    static final String ENTITLEMENTS_POLICY = "REPLACE_CARRY_USAGE";
    static final String USAGE_WARNING =
            "Yeni paketin hakları geçerli olur; mevcut kullanımınız (şube, menü, ürün) yeni pakete taşınır.";
    static final String DOWNGRADE_NEXT_PERIOD_ONLY = "Paket düşürme yalnızca dönem sonunda yapılabilir.";
    static final String NOT_RENEWING_WARNING =
            "Dönem sonu geçişi için otomatik yenilenen bir abonelik gerekir.";
    /** A scheduled change is applied this long before its effective time, ahead of the renewal charge. */
    static final Duration EXECUTION_LEAD = Duration.ofHours(1);

    private final PlanChangeRepository planChangeRepository;
    private final PurchaseRepository purchaseRepository;
    private final PlanPackageRepository planPackageRepository;
    private final FulfillmentService fulfillmentService;
    private final PaymentServiceClient paymentServiceClient;
    private final PaymentRequestMapper paymentRequestMapper;
    private final PurchaseFulfillmentService purchaseFulfillmentService;
    private final PurchaseLogService purchaseLogService;
    private final AppProperties appProperties;
    private final PaymentClientProperties paymentClientProperties;
    private final RecurringAddonBilling recurringAddonBilling;

    @Transactional(readOnly = true)
    public PlanChangePreviewResponse preview(Long merchantId, Long toPackageId) {
        LocalDateTime now = LocalDateTime.now();
        Current current = requireCurrent(merchantId, false, now);
        PlanPackage toPackage = requireTarget(toPackageId, current.purchase().getPackageId());
        BigDecimal toPrice = periodPrice(toPackage, current.period());
        PlanChangeDirection direction = direction(current.price(), toPrice);
        BigDecimal chargeNow = proratedCharge(current, toPrice, now);

        List<String> warnings = new ArrayList<>();
        warnings.add(USAGE_WARNING);
        List<PlanChangeOptionResponse> options = new ArrayList<>();
        if (direction == PlanChangeDirection.UPGRADE && chargeNow.signum() > 0) {
            options.add(PlanChangeOptionResponse.builder()
                    .timing(PlanChangeTiming.IMMEDIATE)
                    .chargeNow(chargeNow)
                    .refundNow(BigDecimal.ZERO)
                    .chargeAtEffective(BigDecimal.ZERO)
                    .effectiveAt(now)
                    .entitlementsPolicy(ENTITLEMENTS_POLICY)
                    .build());
        }
        if (direction == PlanChangeDirection.DOWNGRADE) {
            warnings.add(DOWNGRADE_NEXT_PERIOD_ONLY);
        }
        if (renews(current.purchase())) {
            options.add(PlanChangeOptionResponse.builder()
                    .timing(PlanChangeTiming.NEXT_PERIOD)
                    .chargeNow(BigDecimal.ZERO)
                    .refundNow(BigDecimal.ZERO)
                    .chargeAtEffective(toPrice)
                    .effectiveAt(current.purchase().getExpiresAt())
                    .entitlementsPolicy(ENTITLEMENTS_POLICY)
                    .build());
        } else {
            warnings.add(NOT_RENEWING_WARNING);
        }

        return PlanChangePreviewResponse.builder()
                .fromPurchaseId(current.purchase().getId())
                .fromPackage(toSummary(current.fromPackage()))
                .toPackage(toSummary(toPackage))
                .direction(direction)
                .currentExpiresAt(current.purchase().getExpiresAt())
                .options(List.copyOf(options))
                .warnings(List.copyOf(warnings))
                .hasScheduledChange(planChangeRepository.existsByMerchantIdAndStatus(merchantId, PlanChangeStatus.SCHEDULED))
                .build();
    }

    @Transactional(noRollbackFor = PaymentServiceException.class)
    public PlanChangeResponse request(User user, PlanChangeRequest request, String clientIp) {
        Long merchantId = user.getId();
        LocalDateTime now = LocalDateTime.now();
        Current current = requireCurrent(merchantId, true, now);
        PlanPackage toPackage = requireTarget(request.getToPackageId(), current.purchase().getPackageId());
        BigDecimal toPrice = periodPrice(toPackage, current.period());
        PlanChangeDirection direction = direction(current.price(), toPrice);
        ensureNoOpenChange(merchantId);

        purchaseLogService.log(
                current.purchase().getId(),
                merchantId,
                PurchaseLogAction.PLAN_CHANGE_REQUESTED,
                direction + " " + request.getTiming() + ": " + current.fromPackage().getCode()
                        + " -> " + toPackage.getCode()
        );
        if (request.getTiming() == PlanChangeTiming.NEXT_PERIOD) {
            return schedule(current, toPackage, toPrice, direction, request);
        }
        if (direction != PlanChangeDirection.UPGRADE) {
            throw new BadRequestException(DOWNGRADE_NEXT_PERIOD_ONLY);
        }
        BigDecimal chargeNow = proratedCharge(current, toPrice, now);
        if (chargeNow.signum() <= 0) {
            throw new BadRequestException("Dönemin bitmesine çok az kaldı; geçişi dönem sonuna planlayın");
        }
        return startUpgradePayment(user, current, toPackage, toPrice, chargeNow, request, clientIp, now);
    }

    @Transactional
    public PlanChangeResponse cancel(Long merchantId, Long planChangeId) {
        PlanChange planChange = planChangeRepository.findByIdForUpdate(planChangeId)
                .filter(change -> change.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new NotFoundException("Paket geçişi bulunamadı: " + planChangeId));
        if (planChange.getStatus() != PlanChangeStatus.SCHEDULED) {
            throw new BadRequestException("Sadece planlanmış geçişler iptal edilebilir");
        }
        planChange.setStatus(PlanChangeStatus.CANCELLED);
        planChange.setCompletedAt(LocalDateTime.now());
        planChangeRepository.save(planChange);
        purchaseLogService.log(
                planChange.getFromPurchaseId(),
                merchantId,
                PurchaseLogAction.PLAN_CHANGE_CANCELLED,
                "Planlanmış paket geçişi iptal edildi: " + planChange.getId()
        );
        return toResponse(planChange, null);
    }

    @Transactional(readOnly = true)
    public List<PlanChangeResponse> listMine(Long merchantId) {
        return planChangeRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(change -> toResponse(change, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findDueScheduledIds() {
        return planChangeRepository.findByStatusAndEffectiveAtLessThanEqual(
                        PlanChangeStatus.SCHEDULED,
                        LocalDateTime.now().plus(EXECUTION_LEAD)
                ).stream()
                .map(PlanChange::getId)
                .toList();
    }

    /**
     * Applies a due NEXT_PERIOD change: the current purchase becomes the target package and its
     * subscription renews at the target package's price. The renewal payment then fulfils the new
     * package through the regular payment path (which supersedes the old package and carries usage).
     */
    @Transactional
    public void executeScheduled(Long planChangeId) {
        PlanChange planChange = planChangeRepository.findByIdForUpdate(planChangeId).orElse(null);
        if (planChange == null || planChange.getStatus() != PlanChangeStatus.SCHEDULED) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Purchase purchase = purchaseRepository.findByIdForUpdate(planChange.getFromPurchaseId()).orElse(null);
        if (purchase == null || purchase.getStatus() != PurchaseStatus.ACTIVE || !renews(purchase)) {
            fail(planChange, "Mevcut abonelik artık yenilenmiyor");
            return;
        }
        if (purchase.getExpiresAt() != null && purchase.getExpiresAt().isAfter(now.plus(EXECUTION_LEAD))) {
            // Already renewed at the old price before the change ran: apply it at the next period end.
            planChange.setEffectiveAt(purchase.getExpiresAt());
            planChangeRepository.save(planChange);
            return;
        }
        PlanPackage toPackage = planPackageRepository.findByIdWithItems(planChange.getToPackageId())
                .filter(PlanPackage::isActive)
                .orElse(null);
        if (toPackage == null) {
            fail(planChange, "Hedef paket artık aktif değil");
            return;
        }
        BillingPeriod period = BillingPeriodResolver.resolve(purchase);
        BigDecimal toPrice = periodPrice(toPackage, period);
        boolean switched = paymentServiceClient.switchSubscriptionPlan(
                purchase.getMerchantId(),
                purchase.getSubscriptionId(),
                purchase.getId(),
                toPrice,
                renewalMetadata(purchase.getId(), purchase.getPaymentConversationId(), toPackage, toPrice, period)
        );
        if (!switched) {
            fail(planChange, "Abonelik yeni pakete taşınamadı");
            return;
        }
        String previousCode = purchase.getPackageCode();
        purchase.setPackageId(toPackage.getId());
        purchase.setPackageCode(toPackage.getCode());
        purchase.setPackageName(toPackage.getName());
        purchase.setPrice(toPrice);
        purchase.setListPrice(toPrice);
        purchase.setBasePrice(toPrice);
        purchase.setModulesTotal(BigDecimal.ZERO);
        purchase.setRecurringPrice(toPrice);
        purchaseRepository.save(purchase);
        recurringAddonBilling.syncHost(purchase);

        planChange.setStatus(PlanChangeStatus.COMPLETED);
        planChange.setChargeAmount(toPrice);
        // resultingPurchaseId stays empty: the purchase itself changed package, and that column must
        // stay unique to the IMMEDIATE change whose payment created the purchase.
        planChange.setCompletedAt(now);
        planChangeRepository.save(planChange);
        purchaseLogService.log(
                purchase.getId(),
                purchase.getMerchantId(),
                PurchaseLogAction.PLAN_CHANGE_COMPLETED,
                previousCode + " -> " + toPackage.getCode() + " dönem sonu geçişi uygulandı; yenileme tutarı " + toPrice
        );
    }

    /**
     * Called for every successful payment of a purchase. When the purchase is the target of an
     * IMMEDIATE plan change, activates it until the replaced package's billing date, moves the
     * subscription onto it and supersedes the old purchase. Returns whether it handled the payment.
     */
    @Transactional
    public boolean fulfillUpgradePayment(
            Purchase purchase,
            PaymentCompletedEventDto event,
            PaymentEventMetadata metadata
    ) {
        PlanChange planChange = planChangeRepository.findByResultingPurchaseIdForUpdate(purchase.getId())
                .filter(change -> change.getTiming() == PlanChangeTiming.IMMEDIATE)
                .orElse(null);
        if (planChange == null || planChange.getStatus() == PlanChangeStatus.COMPLETED) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        Purchase from = purchaseRepository.findByIdForUpdate(planChange.getFromPurchaseId()).orElse(null);
        LocalDateTime periodEnd = from != null && from.getExpiresAt() != null && from.getExpiresAt().isAfter(now)
                ? from.getExpiresAt()
                : now.plusDays(1);
        purchaseFulfillmentService.fulfillPlanChangeInstallment(purchase, event, metadata, periodEnd);

        if (from != null) {
            takeOverSubscription(from, purchase);
            if (from.getStatus() == PurchaseStatus.ACTIVE) {
                from.setStatus(PurchaseStatus.SUPERSEDED);
                purchaseRepository.save(from);
            }
        }
        planChange.setStatus(PlanChangeStatus.COMPLETED);
        planChange.setPaymentId(event.getPaymentId());
        planChange.setCompletedAt(now);
        planChangeRepository.save(planChange);
        purchaseLogService.log(
                purchase.getId(),
                purchase.getMerchantId(),
                PurchaseLogAction.PLAN_CHANGE_COMPLETED,
                "Paket yükseltme tamamlandı: " + purchase.getPackageCode() + ", fark " + event.getAmount()
        );
        return true;
    }

    /** The difference payment of an IMMEDIATE change failed. */
    @Transactional
    public void onPaymentFailed(Long purchaseId) {
        planChangeRepository.findByResultingPurchaseId(purchaseId)
                .filter(change -> change.getStatus() == PlanChangeStatus.PENDING_PAYMENT)
                .ifPresent(change -> fail(change, "Fark ödemesi başarısız"));
    }

    private PlanChangeResponse schedule(
            Current current,
            PlanPackage toPackage,
            BigDecimal toPrice,
            PlanChangeDirection direction,
            PlanChangeRequest request
    ) {
        Purchase purchase = current.purchase();
        if (!renews(purchase)) {
            throw new BadRequestException(NOT_RENEWING_WARNING);
        }
        PlanChange planChange = planChangeRepository.save(PlanChange.builder()
                .merchantId(purchase.getMerchantId())
                .fromPurchaseId(purchase.getId())
                .fromPackageId(current.fromPackage().getId())
                .toPackageId(toPackage.getId())
                .direction(direction)
                .timing(PlanChangeTiming.NEXT_PERIOD)
                .status(PlanChangeStatus.SCHEDULED)
                .chargeAmount(toPrice)
                .refundAmount(BigDecimal.ZERO)
                .currency(toPackage.getCurrency())
                .paymentMethodId(request.getPaymentMethodId() != null
                        ? request.getPaymentMethodId()
                        : purchase.getPaymentMethodId())
                .effectiveAt(purchase.getExpiresAt())
                .warningAck(true)
                .build());
        purchaseLogService.log(
                purchase.getId(),
                purchase.getMerchantId(),
                PurchaseLogAction.PLAN_CHANGE_SCHEDULED,
                toPackage.getCode() + " paketine geçiş planlandı: " + planChange.getEffectiveAt()
        );
        return toResponse(planChange, null);
    }

    private PlanChangeResponse startUpgradePayment(
            User user,
            Current current,
            PlanPackage toPackage,
            BigDecimal toPrice,
            BigDecimal chargeNow,
            PlanChangeRequest request,
            String clientIp,
            LocalDateTime now
    ) {
        Purchase from = current.purchase();
        String conversationId = paymentRequestMapper.newPaymentAttemptId(user.getId());
        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .merchantId(from.getMerchantId())
                .packageId(toPackage.getId())
                .packageCode(toPackage.getCode())
                .packageName(toPackage.getName())
                .price(toPrice)
                .listPrice(toPrice)
                .basePrice(toPrice)
                .modulesTotal(BigDecimal.ZERO)
                .recurringPrice(toPrice)
                .currency(toPackage.getCurrency())
                .paymentMode(PaymentMode.CHECKOUT_FORM)
                .paymentStyle(from.getPaymentStyle())
                .billingPeriod(current.period())
                .billingIntervalMonths(current.period().intervalMonths())
                .purchaseType(PurchaseType.PAID)
                .installmentCount(1)
                .paymentMethodId(request.getPaymentMethodId() != null
                        ? request.getPaymentMethodId()
                        : from.getPaymentMethodId())
                .recurringConsent(from.isRecurringConsent())
                .cardBrand(from.getCardBrand())
                .cardLastFour(from.getCardLastFour())
                .billingSnapshot(from.getBillingSnapshot())
                .paymentConversationId(conversationId)
                .status(PurchaseStatus.PENDING)
                .build());
        purchaseFulfillmentService.initializeSchedule(purchase, appProperties.getServiceName());

        PlanChange planChange = planChangeRepository.save(PlanChange.builder()
                .merchantId(from.getMerchantId())
                .fromPurchaseId(from.getId())
                .fromPackageId(current.fromPackage().getId())
                .toPackageId(toPackage.getId())
                .direction(PlanChangeDirection.UPGRADE)
                .timing(PlanChangeTiming.IMMEDIATE)
                .status(PlanChangeStatus.PENDING_PAYMENT)
                .chargeAmount(chargeNow)
                .refundAmount(BigDecimal.ZERO)
                .currency(toPackage.getCurrency())
                .paymentMethodId(purchase.getPaymentMethodId())
                .paymentConversationId(conversationId)
                .resultingPurchaseId(purchase.getId())
                .effectiveAt(now)
                .warningAck(true)
                .build());

        PaymentCheckoutFormResponse checkout;
        try {
            checkout = paymentServiceClient.initializeCheckoutForm(
                    user.getId(),
                    paymentRequestMapper.toPlanChangeCheckoutFormRequest(
                            purchase,
                            user,
                            toPackage,
                            clientIp,
                            appProperties,
                            paymentClientProperties,
                            conversationId,
                            chargeNow
                    )
            );
        } catch (PaymentServiceException exception) {
            purchase.setStatus(PurchaseStatus.FAILED);
            purchaseRepository.save(purchase);
            fail(planChange, "Ödeme başlatılamadı: " + exception.getMessage());
            throw exception;
        }
        if (checkout.getConversationId() != null && !checkout.getConversationId().isBlank()) {
            purchase.setPaymentConversationId(checkout.getConversationId());
            purchaseRepository.save(purchase);
            planChange.setPaymentConversationId(checkout.getConversationId());
            planChangeRepository.save(planChange);
        }
        purchaseLogService.log(
                purchase.getId(),
                purchase.getMerchantId(),
                PurchaseLogAction.PLAN_CHANGE_PAYMENT_STARTED,
                toPackage.getCode() + " paketine geçiş için fark ödemesi başlatıldı: " + chargeNow
        );
        return toResponse(planChange, checkout);
    }

    /** Points the old purchase's subscription at the new purchase so renewals charge the new price. */
    private void takeOverSubscription(Purchase from, Purchase target) {
        String subscriptionId = from.getSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return;
        }
        boolean switched = paymentServiceClient.switchSubscriptionPlan(
                target.getMerchantId(),
                subscriptionId,
                target.getId(),
                target.getRecurringPrice(),
                renewalMetadata(
                        target.getId(),
                        target.getPaymentConversationId(),
                        planPackageRepository.findById(target.getPackageId()).orElse(null),
                        target.getRecurringPrice(),
                        BillingPeriodResolver.resolve(target)
                )
        );
        if (!switched) {
            log.warn("Plan change: subscription not moved to the new purchase. subscriptionId={} purchaseId={}",
                    subscriptionId, target.getId());
            return;
        }
        target.setSubscriptionId(subscriptionId);
        target.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        target.setCancelAtPeriodEnd(false);
        purchaseRepository.save(target);
        recurringAddonBilling.syncHost(target);
    }

    /** What the subscription's renewal payment events must carry to match the purchase they renew. */
    static Map<String, Object> renewalMetadata(
            Long purchaseId,
            String conversationId,
            PlanPackage planPackage,
            BigDecimal price,
            BillingPeriod period
    ) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("purchaseId", purchaseId);
        patch.put("purchaseConversationId", conversationId);
        patch.put("totalAmount", price);
        patch.put("recurringPrice", price);
        patch.put("billingPeriod", period == null ? null : period.name());
        if (planPackage != null) {
            patch.put("packageId", planPackage.getId());
            patch.put("packageCode", planPackage.getCode());
            patch.put("validityDays", planPackage.getValidityDays());
        }
        // Stale descriptions of the first payment; a renewal is the whole package at list price.
        patch.put("cart", null);
        patch.put("planChange", null);
        patch.put("planChangeDifference", null);
        return patch;
    }

    private void ensureNoOpenChange(Long merchantId) {
        if (planChangeRepository.existsByMerchantIdAndStatus(merchantId, PlanChangeStatus.SCHEDULED)) {
            throw new BadRequestException("Zaten planlanmış bir paket geçişi var; önce iptal edin");
        }
        Optional<PlanChange> pending = planChangeRepository.findByMerchantIdAndStatus(merchantId, PlanChangeStatus.PENDING_PAYMENT);
        if (pending.isEmpty()) {
            return;
        }
        boolean stillPayable = pending.get().getResultingPurchaseId() != null
                && purchaseRepository.findById(pending.get().getResultingPurchaseId())
                .map(purchase -> purchase.getStatus() == PurchaseStatus.PENDING)
                .orElse(false);
        if (stillPayable) {
            throw new BadRequestException("Ödeme bekleyen bir paket geçişi var");
        }
        // The checkout was abandoned or timed out without a failure event.
        fail(pending.get(), "Fark ödemesi tamamlanmadı");
    }

    private void fail(PlanChange planChange, String reason) {
        planChange.setStatus(PlanChangeStatus.FAILED);
        planChange.setCompletedAt(LocalDateTime.now());
        planChangeRepository.save(planChange);
        purchaseLogService.log(
                planChange.getFromPurchaseId(),
                planChange.getMerchantId(),
                PurchaseLogAction.PLAN_CHANGE_PAYMENT_FAILED,
                "Paket geçişi başarısız: " + reason
        );
    }

    private Current requireCurrent(Long merchantId, boolean lock, LocalDateTime now) {
        Long purchaseId = fulfillmentService.findActivePackage(merchantId)
                .filter(active -> active.kind() == SubscriptionKind.PAID && active.status() == PackageStatus.ACTIVE)
                .map(ActivePackage::purchaseId)
                .filter(id -> id != null && id > 0)
                .orElseThrow(() -> new BadRequestException("Paket geçişi için aktif ücretli paket gerekli"));
        Purchase purchase = (lock ? purchaseRepository.findByIdForUpdate(purchaseId) : purchaseRepository.findById(purchaseId))
                .filter(found -> merchantId.equals(found.getMerchantId()))
                .filter(found -> found.getStatus() == PurchaseStatus.ACTIVE)
                .filter(found -> found.getPurchaseType() == PurchaseType.PAID)
                .filter(found -> found.getExpiresAt() != null && found.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BadRequestException("Paket geçişi için aktif ücretli paket gerekli"));
        PlanPackage fromPackage = planPackageRepository.findById(purchase.getPackageId())
                .orElseThrow(() -> new BadRequestException("Mevcut paket bulunamadı"));
        BillingPeriod period = BillingPeriodResolver.resolve(purchase);
        return new Current(purchase, fromPackage, period, periodPrice(fromPackage, period));
    }

    private PlanPackage requireTarget(Long toPackageId, Long fromPackageId) {
        PlanPackage toPackage = planPackageRepository.findByIdWithItems(toPackageId)
                .orElseThrow(() -> new BadRequestException("Paket bulunamadı: " + toPackageId));
        if (!toPackage.isActive() || !toPackage.isPurchasable() || toPackage.isSystemManaged()
                || toPackage.getItems() == null || toPackage.getItems().isEmpty()) {
            throw new BadRequestException("Bu paket geçiş hedefi olamaz");
        }
        if (toPackage.getId().equals(fromPackageId)) {
            throw new BadRequestException("Aynı pakete geçiş yapılamaz");
        }
        return toPackage;
    }

    /** True when the purchase has a subscription that will renew at period end. */
    private static boolean renews(Purchase purchase) {
        return purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                && purchase.getSubscriptionId() != null
                && !purchase.getSubscriptionId().isBlank()
                && !purchase.isCancelAtPeriodEnd()
                && purchase.getSubscriptionStatus() != SubscriptionStatus.CANCELLED;
    }

    static BigDecimal periodPrice(PlanPackage planPackage, BillingPeriod period) {
        BigDecimal price = period == BillingPeriod.YEARLY
                ? planPackage.effectiveYearlyPrice()
                : planPackage.effectiveMonthlyPrice();
        if (price == null || price.signum() <= 0) {
            throw new BadRequestException("Paket dönem fiyatı geçersiz: " + planPackage.getCode());
        }
        return price;
    }

    static PlanChangeDirection direction(BigDecimal fromPrice, BigDecimal toPrice) {
        int cmp = toPrice.compareTo(fromPrice);
        if (cmp > 0) {
            return PlanChangeDirection.UPGRADE;
        }
        return cmp < 0 ? PlanChangeDirection.DOWNGRADE : PlanChangeDirection.LATERAL;
    }

    private static BigDecimal proratedCharge(Current current, BigDecimal toPrice, LocalDateTime now) {
        Purchase purchase = current.purchase();
        int intervalMonths = current.period().intervalMonths();
        LocalDateTime periodEnd = purchase.getExpiresAt();
        // The whole billing period, even when this purchase itself started mid-period (an earlier upgrade).
        LocalDateTime periodStart = intervalMonths > 0 || purchase.getStartsAt() == null
                ? periodEnd.minusMonths(Math.max(intervalMonths, 1))
                : purchase.getStartsAt();
        return proratedCharge(current.price(), toPrice, periodStart, periodEnd, now);
    }

    /** (newPrice - oldPrice) x remainingDays / periodDays, HALF_UP to 2 decimals; zero unless an upgrade. */
    static BigDecimal proratedCharge(
            BigDecimal fromPrice,
            BigDecimal toPrice,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            LocalDateTime now
    ) {
        BigDecimal delta = toPrice.subtract(fromPrice);
        if (delta.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        long periodDays = Math.max(ChronoUnit.DAYS.between(periodStart, periodEnd), 1);
        long remainingDays = Math.min(Math.max(ChronoUnit.DAYS.between(now, periodEnd), 0), periodDays);
        return delta.multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
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

    private PlanChangeResponse toResponse(PlanChange planChange, PaymentCheckoutFormResponse checkout) {
        PlanPackage from = planPackageRepository.findById(planChange.getFromPackageId()).orElse(null);
        PlanPackage to = planPackageRepository.findById(planChange.getToPackageId()).orElse(null);
        return PlanChangeResponse.builder()
                .id(planChange.getId())
                .merchantId(planChange.getMerchantId())
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
                .conversationId(checkout != null && checkout.getConversationId() != null
                        ? checkout.getConversationId()
                        : planChange.getPaymentConversationId())
                .token(checkout == null ? null : checkout.getToken())
                .paymentPageUrl(checkout == null ? null : checkout.getPaymentPageUrl())
                .checkoutFormContent(checkout == null ? null : checkout.getCheckoutFormContent())
                .build();
    }

    private record Current(Purchase purchase, PlanPackage fromPackage, BillingPeriod period, BigDecimal price) {
    }
}
