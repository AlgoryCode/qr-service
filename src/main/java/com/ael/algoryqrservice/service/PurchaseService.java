package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PaymentEventInbox;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.dto.PaymentEventMetadata;
import com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.model.dto.PurchaseFulfillmentResponse;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PurchaseCancellationReason;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.RefundStatus;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PaymentEventInboxRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseService {

    private final PlanPackageService planPackageService;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseLogService purchaseLogService;
    private final EntitlementService entitlementService;
    private final PaymentServiceClient paymentServiceClient;
    private final PaymentRequestMapper paymentRequestMapper;
    private final AppProperties appProperties;
    private final PaymentEventInboxRepository paymentEventInboxRepository;
    private final PackageActivationService packageActivationService;
    private final PurchaseFulfillmentService purchaseFulfillmentService;
    private final BillingAddressService billingAddressService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final PlanChangeService planChangeService;
    private final SubscriptionRefundPolicy subscriptionRefundPolicy;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public PurchaseInitiateResponse purchase(User user, PurchaseRequest request, String clientIp) {
        PlanPackage planPackage = planPackageService.findActivePackage(request.getPackageId());
        if (!planPackage.isPurchasable() || planPackage.isSystemManaged()
                || CatalogPackages.FREE_PACKAGE.equals(planPackage.getCode())) {
            throw new BadRequestException("Bu paket satın alınamaz");
        }
        if (!request.isPaymentPlanValid()) {
            throw new BadRequestException("Geçersiz ödeme planı");
        }

        purchaseLogService.log(
                null,
                user.getId(),
                PurchaseLogAction.PURCHASE_STARTED,
                planPackage.getName() + " paketi satın alma başlatıldı"
        );

        BillingSnapshot billingSnapshot = request.getBillingAddress() != null
                ? billingAddressService.legacySnapshot(user.getId(), request.getBillingAddress(), request.getIdentityNumber())
                : billingAddressService.resolveSnapshot(
                        user.getId(), request.getBillingAddressId(), request.getInlineBillingAddress());
        PaymentStyle paymentStyle = request.resolvedPaymentStyle();
        var billingPeriod = request.resolvedBillingPeriod();
        BigDecimal chargeAmount = billingPeriod == com.ael.algoryqrservice.model.enums.BillingPeriod.YEARLY
                ? planPackage.effectiveYearlyPrice()
                : planPackage.effectiveMonthlyPrice();
        if (chargeAmount == null || chargeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Paket fiyati gecersiz");
        }
        CardSnapshot cardSnapshot = resolveCardSnapshot(user.getId(), request.getPaymentMethodId());
        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .userId(user.getId())
                .packageId(planPackage.getId())
                .packageCode(planPackage.getCode())
                .packageName(planPackage.getName())
                .price(chargeAmount)
                .currency(planPackage.getCurrency())
                .paymentMode(request.getPaymentMode())
                .paymentStyle(paymentStyle)
                .billingPeriod(billingPeriod)
                .billingIntervalMonths(billingPeriod.intervalMonths())
                .purchaseType(PurchaseType.PAID)
                .installmentCount(1)
                .paymentMethodId(request.getPaymentMethodId())
                .cardBrand(cardSnapshot.brand())
                .cardLastFour(cardSnapshot.lastFour())
                .billingSnapshot(billingSnapshot)
                .status(PurchaseStatus.PENDING)
                .build());

        purchase.setPaymentConversationId(paymentRequestMapper.buildConversationId(purchase.getId()));
        purchaseRepository.save(purchase);
        purchaseFulfillmentService.initializeSchedule(purchase, appProperties.getServiceName());

        purchaseLogService.log(
                purchase.getId(),
                user.getId(),
                PurchaseLogAction.PURCHASE_PAYMENT_PENDING,
                planPackage.getName() + " paketi ödeme bekliyor"
        );

        try {
            PaymentThreeDsRequest paymentRequest = paymentRequestMapper.toThreeDsRequest(
                    purchase,
                    user,
                    planPackage,
                    request,
                    clientIp,
                    appProperties
            );
            PaymentThreeDsResponse paymentResponse = request.getPaymentMode() == PaymentMode.DIRECT
                    ? paymentServiceClient.createDirectPayment(paymentRequest)
                    : paymentServiceClient.initializeThreeDsPayment(paymentRequest);

            return PurchaseInitiateResponse.builder()
                    .purchaseId(purchase.getId())
                    .status(purchase.getStatus())
                    .conversationId(paymentResponse.getConversationId())
                    .paymentHtml(paymentResponse.getHtmlContent())
                    .build();
        } catch (PaymentServiceException exception) {
            purchase.setStatus(PurchaseStatus.FAILED);
            purchaseRepository.save(purchase);
            purchaseLogService.log(
                    purchase.getId(),
                    user.getId(),
                    PurchaseLogAction.PURCHASE_PAYMENT_FAILED,
                    "Ödeme başlatılamadı: " + exception.getMessage()
            );
            throw exception;
        }
    }

    @Transactional
    public void handlePaymentSuccess(PaymentCompletedEventDto event) {
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }

        PaymentEventMetadata metadata = PaymentEventMetadata.from(event);
        Purchase purchase = purchaseRepository.findByIdForUpdate(metadata.purchaseId())
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + metadata.purchaseId()));
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }
        validatePaidEvent(event, metadata, purchase);

        if (purchase.getStatus() == PurchaseStatus.CANCELLED
                && purchase.getCancellationReason() != PurchaseCancellationReason.PAYMENT_TIMEOUT) {
            throw new InvalidPaymentEventException("Manually cancelled purchase cannot be fulfilled");
        }
        PlanPackage planPackage = planPackageService.findPackage(purchase.getPackageId());
        purchaseFulfillmentService.fulfillPaidInstallment(purchase, planPackage, event, metadata);
        planChangeService.onPurchaseActivated(purchase);

        purchaseLogService.log(
                purchase.getId(),
                purchase.getUserId(),
                PurchaseLogAction.PURCHASE_COMPLETED,
                purchase.getPackageName() + " paketi " + metadata.installmentNumber()
                        + "/" + metadata.installmentCount() + " taksiti işlendi"
        );
        markEventProcessed(event, purchase.getId());
    }

    @Transactional
    public void handlePaymentFailed(PaymentCompletedEventDto event) {
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }

        PaymentEventMetadata metadata = PaymentEventMetadata.from(event);
        Purchase purchase = purchaseRepository.findByIdForUpdate(metadata.purchaseId())
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + metadata.purchaseId()));
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }
        validateIdentity(event, metadata, purchase);

        if (purchase.getStatus() == PurchaseStatus.ACTIVE
                || purchase.getStatus() == PurchaseStatus.SUPERSEDED
                || purchase.getStatus() == PurchaseStatus.EXPIRED) {
            if (purchase.getStatus() == PurchaseStatus.ACTIVE
                    && purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
                purchase.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
                purchaseRepository.save(purchase);
                log.warn(
                        "Marked subscription PAST_DUE after renewal failure. purchaseId={} eventId={}",
                        purchase.getId(),
                        event.getEventId()
                );
            } else {
                log.warn(
                        "Ignoring payment failed event for non-pending purchase. purchaseId={} status={} eventId={}",
                        purchase.getId(),
                        purchase.getStatus(),
                        event.getEventId()
                );
            }
            markEventProcessed(event, purchase.getId());
            return;
        }

        if (purchase.getStatus() == PurchaseStatus.PENDING) {
            purchase.setStatus(PurchaseStatus.FAILED);
            if (event.getPaymentId() != null) {
                purchase.setPaymentId(event.getPaymentId());
            }
            purchaseRepository.save(purchase);
            String reason = event.getFailureReason() == null || event.getFailureReason().isBlank()
                    ? "ödeme başarısız"
                    : event.getFailureReason();
            purchaseLogService.log(
                    purchase.getId(),
                    purchase.getUserId(),
                    PurchaseLogAction.PURCHASE_PAYMENT_FAILED,
                    purchase.getPackageName() + " paketi ödemesi başarısız: " + reason
            );
            planChangeService.onPurchasePaymentFailed(purchase);
        }
        purchaseFulfillmentService.recordUnpaidInstallment(
                purchase,
                event,
                metadata,
                FulfillmentStatus.FAILED
        );
        markEventProcessed(event, purchase.getId());
    }

    @Transactional
    public void handlePaymentOverdue(PaymentCompletedEventDto event) {
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }
        PaymentEventMetadata metadata = PaymentEventMetadata.from(event);
        Purchase purchase = purchaseRepository.findByIdForUpdate(metadata.purchaseId())
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + metadata.purchaseId()));
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }
        validateIdentity(event, metadata, purchase);
        purchaseFulfillmentService.recordUnpaidInstallment(
                purchase,
                event,
                metadata,
                FulfillmentStatus.OVERDUE
        );
        markEventProcessed(event, purchase.getId());
    }

    @Transactional
    public void handlePaymentRefunded(PaymentCompletedEventDto event) {
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }
        PaymentEventMetadata metadata = PaymentEventMetadata.from(event);
        Purchase purchase = purchaseRepository.findByIdForUpdate(metadata.purchaseId())
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + metadata.purchaseId()));
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }
        validateIdentity(event, metadata, purchase);
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            markEventProcessed(event, purchase.getId());
            return;
        }
        purchaseFulfillmentService.revokeInstallment(purchase, event, metadata);
        markEventProcessed(event, purchase.getId());
    }

    @Transactional
    public void handleSubscriptionCancelledAtPeriodEnd(PaymentCompletedEventDto event) {
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }
        Long purchaseId = null;
        if (event.getSourceReferenceId() != null && !event.getSourceReferenceId().isBlank()) {
            try {
                purchaseId = Long.valueOf(event.getSourceReferenceId());
            } catch (NumberFormatException ignored) {
                purchaseId = null;
            }
        }
        if (purchaseId == null && event.getSourceMetadata() != null && event.getSourceMetadata().get("purchaseId") != null) {
            purchaseId = Long.valueOf(String.valueOf(event.getSourceMetadata().get("purchaseId")));
        }
        if (purchaseId == null) {
            throw new InvalidPaymentEventException("Purchase id missing for period-end cancel event");
        }
        final Long resolvedPurchaseId = purchaseId;
        Purchase purchase = purchaseRepository.findByIdForUpdate(resolvedPurchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + resolvedPurchaseId));
        if (paymentEventInboxRepository.existsByEventId(event.getEventId())) {
            return;
        }
        purchase.setCancelAtPeriodEnd(true);
        purchase.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        purchaseRepository.save(purchase);
        markEventProcessed(event, purchase.getId());
    }

    @Transactional
    public boolean reconcilePaidPendingPurchase(Long purchaseId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId).orElse(null);
        if (purchase == null
                || purchase.getStatus() != PurchaseStatus.PENDING
                || purchase.getPaymentConversationId() == null
                || purchase.getPaymentConversationId().isBlank()) {
            return false;
        }

        BillingPaymentDtos.RefundablePayment payment;
        try {
            payment = paymentServiceClient.getRefundablePayment(
                    purchase.getUserId(),
                    purchase.getPaymentConversationId()
            );
        } catch (PaymentServiceException exception) {
            log.debug(
                    "Pending purchase payment lookup skipped. purchaseId={} reason={}",
                    purchaseId,
                    exception.getMessage()
            );
            return false;
        }

        if (!payment.isSuccess()) {
            return false;
        }

        PaymentCompletedEventDto event = buildReconcileSuccessEvent(purchase, payment);
        handlePaymentSuccess(event);
        log.info(
                "Pending purchase activated by reconcile. purchaseId={} conversationId={} eventId={}",
                purchase.getId(),
                purchase.getPaymentConversationId(),
                event.getEventId()
        );
        return true;
    }

    @Transactional
    public void cancelExpiredPendingPurchases(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Purchase> pendingPurchases = purchaseRepository.findByStatusAndPurchasedAtBefore(
                PurchaseStatus.PENDING,
                threshold
        );

        for (Purchase pending : pendingPurchases) {
            Purchase purchase = purchaseRepository.findByIdForUpdate(pending.getId()).orElse(null);
            if (purchase == null || purchase.getStatus() != PurchaseStatus.PENDING) {
                continue;
            }

            if (purchase.getPaymentConversationId() != null && !purchase.getPaymentConversationId().isBlank()) {
                try {
                    BillingPaymentDtos.RefundablePayment payment = paymentServiceClient.getRefundablePayment(
                            purchase.getUserId(),
                            purchase.getPaymentConversationId()
                    );
                    if (payment.isSuccess()) {
                        PaymentCompletedEventDto event = buildReconcileSuccessEvent(purchase, payment);
                        handlePaymentSuccess(event);
                        log.info(
                                "Expired pending purchase activated instead of cancel. purchaseId={} conversationId={}",
                                purchase.getId(),
                                purchase.getPaymentConversationId()
                        );
                        continue;
                    }
                } catch (PaymentServiceException exception) {
                    log.warn(
                            "Expired pending payment lookup failed; cancelling. purchaseId={} reason={}",
                            purchase.getId(),
                            exception.getMessage()
                    );
                }
            }

            purchase.setStatus(PurchaseStatus.CANCELLED);
            purchase.setCancellationReason(PurchaseCancellationReason.PAYMENT_TIMEOUT);
            purchaseRepository.save(purchase);
            purchaseLogService.log(
                    purchase.getId(),
                    purchase.getUserId(),
                    PurchaseLogAction.PURCHASE_CANCELLED,
                    purchase.getPackageName() + " paketi ödeme zaman aşımı nedeniyle iptal edildi"
            );
        }
    }

    private PaymentCompletedEventDto buildReconcileSuccessEvent(
            Purchase purchase,
            BillingPaymentDtos.RefundablePayment payment
    ) {
        PlanPackage planPackage = planPackageService.findPackage(purchase.getPackageId());
        int installmentCount = purchase.getInstallmentCount() == null || purchase.getInstallmentCount() < 1
                ? 1
                : purchase.getInstallmentCount();
        int installmentNumber = 1;
        BigDecimal amount = resolveExpectedInstallmentAmount(purchase, installmentNumber, installmentCount);
        LocalDateTime periodStart = LocalDateTime.now();
        int validityDays = planPackage.getValidityDays() == null || planPackage.getValidityDays() < 1
                ? 30
                : planPackage.getValidityDays();
        LocalDateTime periodEnd = periodStart.plusDays(validityDays);
        String installmentId = payment.paymentId() != null && !payment.paymentId().isBlank()
                ? payment.paymentId()
                : purchase.getPaymentConversationId();
        String eventId = "reconcile:" + purchase.getPaymentConversationId() + ":" + installmentNumber;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("purchaseId", purchase.getId());
        metadata.put("userId", purchase.getUserId());
        metadata.put("packageId", purchase.getPackageId());
        metadata.put("packageCode", purchase.getPackageCode());
        metadata.put("purchaseConversationId", purchase.getPaymentConversationId());
        metadata.put("installmentId", installmentId);
        metadata.put("installmentNumber", installmentNumber);
        metadata.put("installmentCount", installmentCount);
        metadata.put("periodStart", periodStart.toString());
        metadata.put("periodEnd", periodEnd.toString());

        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId(eventId);
        event.setEventType("payment.success");
        event.setServiceName(appProperties.getServiceName());
        event.setPaymentId(payment.paymentId());
        event.setConversationId(purchase.getPaymentConversationId());
        event.setSourceReferenceId(String.valueOf(purchase.getId()));
        event.setSourceMetadata(metadata);
        event.setPurchaseId(String.valueOf(purchase.getId()));
        event.setUserId(String.valueOf(purchase.getUserId()));
        event.setPackageId(String.valueOf(purchase.getPackageId()));
        event.setPackageCode(purchase.getPackageCode());
        event.setInstallmentId(installmentId);
        event.setInstallmentNumber(installmentNumber);
        event.setInstallmentCount(installmentCount);
        event.setAmount(amount);
        event.setCurrency(purchase.getCurrency());
        event.setPeriodStart(periodStart.toString());
        event.setPeriodEnd(periodEnd.toString());
        return event;
    }

    private BigDecimal resolveExpectedInstallmentAmount(
            Purchase purchase,
            int installmentNumber,
            int installmentCount
    ) {
        if (purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                || installmentCount <= 1) {
            return purchase.getPrice();
        }
        boolean legacySplit = purchase.getPaymentStyle() == PaymentStyle.ONE_TIME && installmentCount > 1;
        if (!legacySplit) {
            return purchase.getPrice();
        }
        BigDecimal standardAmount = purchase.getPrice().divide(
                BigDecimal.valueOf(installmentCount),
                2,
                RoundingMode.DOWN
        );
        if (installmentNumber == installmentCount) {
            return purchase.getPrice().subtract(standardAmount.multiply(
                    BigDecimal.valueOf(installmentCount - 1L)
            ));
        }
        return standardAmount;
    }

    @Transactional
    public List<PurchaseResponse> getUserPurchases(Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);
        return purchaseRepository.findByUserIdOrderByPurchasedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PurchaseSummaryResponse getPurchaseSummary(Long purchaseId, Long userId) {
        entitlementService.expireDuePurchasesForUser(userId);
        Purchase purchase = findUserPurchase(purchaseId, userId);
        return toSummary(purchase);
    }

    @Transactional(readOnly = true)
    public List<PurchaseFulfillmentResponse> getPurchaseInstallments(Long purchaseId, Long userId) {
        findUserPurchase(purchaseId, userId);
        return purchaseFulfillmentService.getFulfillments(purchaseId);
    }

    @Transactional(readOnly = true)
    public PurchaseSummaryResponse getPurchaseSummaryAdmin(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));
        return toSummary(purchase);
    }

    @Transactional
    public PurchaseResponse expirePurchase(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

        if (purchase.getStatus() == PurchaseStatus.EXPIRED) {
            throw new BadRequestException("Paket zaten süresi dolmuş");
        }
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new BadRequestException("Paket zaten iptal edilmiş");
        }
        if (purchase.getStatus() == PurchaseStatus.PENDING) {
            throw new BadRequestException("Ödeme bekleyen paket süresi doldurulamaz");
        }
        if (purchase.getStatus() == PurchaseStatus.FAILED) {
            throw new BadRequestException("Başarısız paket süresi doldurulamaz");
        }

        if (purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                && purchase.getSubscriptionId() != null
                && !purchase.getSubscriptionId().isBlank()
                && purchase.getSubscriptionStatus() != SubscriptionStatus.CANCELLED) {
            try {
                paymentServiceClient.cancelSubscription(purchase.getUserId(), purchase.getSubscriptionId());
                purchase.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
                purchaseRepository.save(purchase);
            } catch (PaymentServiceException exception) {
                log.warn(
                        "Admin expire remote subscription cancel failed. purchaseId={} reason={}",
                        purchaseId,
                        exception.getMessage()
                );
            }
        }

        entitlementService.expirePurchase(purchase);
        packageActivationService.ensureFreePackage(purchase.getUserId());
        menuPublicAccessService.syncForUser(purchase.getUserId());
        return toResponse(purchaseRepository.findById(purchaseId).orElseThrow());
    }

    @Transactional
    public PurchaseResponse cancelMyPurchase(Long purchaseId, Long userId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

        if (!purchase.getUserId().equals(userId)) {
            throw new UnauthorizedException("Bu satın alıma erişim yetkiniz yok");
        }

        validateUserCancellable(purchase);
        if (isPaidActiveSubscription(purchase)) {
            throw new BadRequestException(
                    "Aktif abonelik icin donem sonu iptal veya iade ile iptal kullanin"
            );
        }
        return finalizeImmediateCancel(purchase, userId, null);
    }

    @Transactional
    public PurchaseResponse cancelAtPeriodEnd(Long purchaseId, Long userId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

        if (!purchase.getUserId().equals(userId)) {
            throw new UnauthorizedException("Bu satın alıma erişim yetkiniz yok");
        }
        validatePaidActiveSubscription(purchase);
        if (purchase.isCancelAtPeriodEnd()) {
            return toResponse(purchase);
        }

        requireSubscriptionId(purchase);
        paymentServiceClient.cancelSubscriptionAtPeriodEnd(userId, purchase.getSubscriptionId());
        purchase.setCancelAtPeriodEnd(true);
        purchaseRepository.save(purchase);

        purchaseLogService.log(
                purchase.getId(),
                userId,
                PurchaseLogAction.PURCHASE_CANCEL_AT_PERIOD_END,
                purchase.getPackageName() + " aboneligi donem sonunda bitirilecek"
        );
        return toResponse(purchase);
    }

    @Transactional
    public PurchaseResponse resumeRenewal(Long purchaseId, Long userId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

        if (!purchase.getUserId().equals(userId)) {
            throw new UnauthorizedException("Bu satın alıma erişim yetkiniz yok");
        }
        validatePaidActiveSubscription(purchase);
        if (!purchase.isCancelAtPeriodEnd()) {
            return toResponse(purchase);
        }

        requireSubscriptionId(purchase);
        paymentServiceClient.resumeSubscription(userId, purchase.getSubscriptionId());
        purchase.setCancelAtPeriodEnd(false);
        purchaseRepository.save(purchase);

        purchaseLogService.log(
                purchase.getId(),
                userId,
                PurchaseLogAction.PURCHASE_RENEWAL_RESUMED,
                purchase.getPackageName() + " abonelik yenilemesi tekrar acildi"
        );
        return toResponse(purchase);
    }

    public PurchaseResponse cancelWithRefund(Long purchaseId, Long userId, String clientIp) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        RefundPrep prep = tx.execute(status -> prepareRefund(purchaseId, userId));
        if (prep == null) {
            throw new BadRequestException("Iade hazirligi basarisiz");
        }

        try {
            paymentServiceClient.refundPayment(userId, prep.conversationId(), prep.amount(), clientIp);
        } catch (RuntimeException exception) {
            tx.executeWithoutResult(status -> clearPendingRefund(purchaseId));
            throw exception;
        }

        return tx.execute(status -> completeRefundCancel(purchaseId, userId, prep.amount()));
    }

    private RefundPrep prepareRefund(Long purchaseId, Long userId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

        if (!purchase.getUserId().equals(userId)) {
            throw new UnauthorizedException("Bu satın alıma erişim yetkiniz yok");
        }
        validatePaidActiveSubscription(purchase);
        if (purchase.getRefundedAt() != null || purchase.getRefundStatus() == RefundStatus.COMPLETED) {
            throw new BadRequestException("Bu donem icin iade zaten yapildi");
        }
        if (purchase.getRefundStatus() == RefundStatus.NEEDS_RECONCILE) {
            throw new BadRequestException("Iade tamamlandi; abonelik senkronu bekleniyor");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!subscriptionRefundPolicy.isRefundEligible(purchase, now)) {
            throw new BadRequestException(
                    "Iade penceresi kapandi. Yalnizca donem sonunda bitirme kullanabilirsiniz"
            );
        }

        String conversationId = resolveRefundConversationId(purchase);
        if (conversationId == null || conversationId.isBlank()) {
            throw new BadRequestException("Iade icin odeme kaydi bulunamadi");
        }

        BillingPaymentDtos.RefundablePayment refundable =
                paymentServiceClient.getRefundablePayment(userId, conversationId);
        BigDecimal remaining = refundable.remaining() == null ? BigDecimal.ZERO : refundable.remaining();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Iade edilecek tutar bulunamadi");
        }

        purchase.setRefundStatus(RefundStatus.PENDING);
        purchaseRepository.save(purchase);
        purchaseLogService.log(
                purchase.getId(),
                userId,
                PurchaseLogAction.PURCHASE_REFUND_STARTED,
                purchase.getPackageName() + " abonelik iadesi baslatildi: " + remaining
        );
        return new RefundPrep(conversationId, remaining);
    }

    private void clearPendingRefund(Long purchaseId) {
        purchaseRepository.findByIdForUpdate(purchaseId).ifPresent(purchase -> {
            if (purchase.getRefundStatus() == RefundStatus.PENDING) {
                purchase.setRefundStatus(RefundStatus.NONE);
                purchaseRepository.save(purchase);
            }
        });
    }

    private PurchaseResponse completeRefundCancel(Long purchaseId, Long userId, BigDecimal refundedAmount) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

        purchase.setRefundedAt(LocalDateTime.now());
        purchase.setRefundStatus(RefundStatus.COMPLETED);
        purchaseLogService.log(
                purchase.getId(),
                userId,
                PurchaseLogAction.PURCHASE_REFUND_COMPLETED,
                purchase.getPackageName() + " abonelik iadesi tamamlandi: " + refundedAmount
        );

        try {
            cancelRemoteSubscriptionIfRequired(purchase, userId);
            return applyLocalImmediateCancel(purchase, userId, refundedAmount);
        } catch (PaymentServiceException exception) {
            log.error(
                    "Remote subscription cancel failed after refund; marking NEEDS_RECONCILE. purchaseId={}",
                    purchaseId,
                    exception
            );
            PurchaseResponse response = applyLocalImmediateCancel(purchase, userId, refundedAmount);
            purchase.setRefundStatus(RefundStatus.NEEDS_RECONCILE);
            purchaseRepository.save(purchase);
            return response;
        }
    }

    @Transactional
    public void reconcileNeedsReconcileRefunds() {
        List<Purchase> stuck = purchaseRepository.findByRefundStatus(RefundStatus.NEEDS_RECONCILE);
        for (Purchase purchase : stuck) {
            try {
                Purchase locked = purchaseRepository.findByIdForUpdate(purchase.getId()).orElse(null);
                if (locked == null || locked.getRefundStatus() != RefundStatus.NEEDS_RECONCILE) {
                    continue;
                }
                if (locked.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                        && locked.getSubscriptionId() != null
                        && !locked.getSubscriptionId().isBlank()) {
                    paymentServiceClient.cancelSubscription(locked.getUserId(), locked.getSubscriptionId());
                    locked.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
                }
                locked.setRefundStatus(RefundStatus.COMPLETED);
                purchaseRepository.save(locked);
            } catch (RuntimeException exception) {
                log.warn(
                        "Refund reconcile still pending. purchaseId={} reason={}",
                        purchase.getId(),
                        exception.getMessage()
                );
            }
        }
    }

    private String resolveRefundConversationId(Purchase purchase) {
        if (purchase.getCurrentPeriodConversationId() != null
                && !purchase.getCurrentPeriodConversationId().isBlank()) {
            return purchase.getCurrentPeriodConversationId();
        }
        return purchase.getPaymentConversationId();
    }

    private PurchaseResponse finalizeImmediateCancel(Purchase purchase, Long userId, BigDecimal refundedAmount) {
        cancelRemoteSubscriptionIfRequired(purchase, userId);
        return applyLocalImmediateCancel(purchase, userId, refundedAmount);
    }

    private PurchaseResponse applyLocalImmediateCancel(Purchase purchase, Long userId, BigDecimal refundedAmount) {
        LocalDateTime cancelledAt = LocalDateTime.now();
        boolean wasActive = purchase.getStatus() == PurchaseStatus.ACTIVE;
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setCancellationReason(PurchaseCancellationReason.MANUAL);
        purchase.setExpiresAt(cancelledAt);
        purchase.setCancelAtPeriodEnd(false);
        if (purchase.getSubscriptionStatus() != null
                && purchase.getSubscriptionStatus() != SubscriptionStatus.CANCELLED) {
            purchase.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        }
        purchaseRepository.save(purchase);

        if (wasActive) {
            entitlementService.revokeForCancelledPurchase(purchase);
            menuPublicAccessService.deactivateActiveMenusForUser(userId);
        }
        purchaseFulfillmentService.cancelOpenFulfillments(purchase.getId());
        planChangeService.cancelScheduledForUser(userId);
        packageActivationService.ensureFreePackage(userId);
        menuPublicAccessService.syncForUser(userId);

        String detail = purchase.getPackageName() + " paketi kullanıcı tarafından iptal edildi";
        if (refundedAmount != null) {
            detail = detail + " (iade: " + refundedAmount + ")";
        }
        purchaseLogService.log(
                purchase.getId(),
                userId,
                PurchaseLogAction.PURCHASE_CANCELLED,
                detail
        );
        return toResponse(purchase);
    }

    private record RefundPrep(String conversationId, BigDecimal amount) {
    }

    private void validateUserCancellable(Purchase purchase) {
        if (CatalogPackages.FREE_PACKAGE.equals(purchase.getPackageCode())
                || purchase.getPurchaseType() == PurchaseType.FREE
                || purchase.getPurchaseType() == PurchaseType.SYSTEM_GRANT) {
            throw new BadRequestException("Bu paket kullanıcı tarafından iptal edilemez");
        }

        switch (purchase.getStatus()) {
            case ACTIVE, PENDING -> {
            }
            case CANCELLED -> throw new BadRequestException("Paket zaten iptal edilmiş");
            case EXPIRED -> throw new BadRequestException("Süresi dolmuş paket iptal edilemez");
            case FAILED -> throw new BadRequestException("Başarısız paket iptal edilemez");
            case SUPERSEDED -> throw new BadRequestException("Yerine geçen paket iptal edilemez");
        }
    }

    private void validatePaidActiveSubscription(Purchase purchase) {
        validateUserCancellable(purchase);
        if (!isPaidActiveSubscription(purchase)) {
            throw new BadRequestException("Bu islem yalnizca aktif abonelikler icin gecerlidir");
        }
    }

    private boolean isPaidActiveSubscription(Purchase purchase) {
        return purchase.getStatus() == PurchaseStatus.ACTIVE
                && purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION
                && purchase.getPurchaseType() == PurchaseType.PAID;
    }

    private void requireSubscriptionId(Purchase purchase) {
        String subscriptionId = purchase.getSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new BadRequestException(
                    "Abonelik kimligi bulunamadigi icin paket guvenli sekilde iptal edilemiyor"
            );
        }
    }

    private void cancelRemoteSubscriptionIfRequired(Purchase purchase, Long userId) {
        if (purchase.getPaymentStyle() != PaymentStyle.SUBSCRIPTION) {
            return;
        }
        String subscriptionId = purchase.getSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            if (purchase.getStatus() == PurchaseStatus.PENDING) {
                return;
            }
            throw new BadRequestException(
                    "Abonelik kimligi bulunamadigi icin paket guvenli sekilde iptal edilemiyor"
            );
        }
        paymentServiceClient.cancelSubscription(userId, subscriptionId);
        purchase.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public Purchase findUserPurchase(Long purchaseId, Long userId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

        if (!purchase.getUserId().equals(userId)) {
            throw new UnauthorizedException("Bu satın alıma erişim yetkiniz yok");
        }

        return purchase;
    }

    private void validatePaidEvent(
            PaymentCompletedEventDto event,
            PaymentEventMetadata metadata,
            Purchase purchase
    ) {
        validateIdentity(event, metadata, purchase);
        if (event.getAmount() == null) {
            throw new InvalidPaymentEventException("Payment amount is missing");
        }
        int installmentNumber = metadata.installmentNumber();
        if (installmentNumber < 1) {
            throw new InvalidPaymentEventException("Installment metadata does not match purchase");
        }
        if (purchase.getPaymentStyle() == PaymentStyle.SUBSCRIPTION) {
            if (purchase.getPrice().compareTo(event.getAmount()) != 0) {
                throw new InvalidPaymentEventException("Payment amount does not match purchase installment");
            }
        } else {
            int installmentCount = purchase.getInstallmentCount() == null || purchase.getInstallmentCount() < 1
                    ? 1
                    : purchase.getInstallmentCount();
            if (installmentNumber > installmentCount) {
                throw new InvalidPaymentEventException("Installment metadata does not match purchase");
            }
            if (!metadata.installmentCount().equals(installmentCount)) {
                throw new InvalidPaymentEventException("Installment metadata does not match purchase");
            }
            BigDecimal expectedAmount = resolveExpectedInstallmentAmount(purchase, installmentNumber, installmentCount);
            if (expectedAmount.compareTo(event.getAmount()) != 0) {
                throw new InvalidPaymentEventException("Payment amount does not match purchase installment");
            }
        }
        if (!metadata.periodEnd().isAfter(metadata.periodStart())) {
            throw new InvalidPaymentEventException("Payment period is invalid");
        }
    }

    private void validateIdentity(
            PaymentCompletedEventDto event,
            PaymentEventMetadata metadata,
            Purchase purchase
    ) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new InvalidPaymentEventException("Payment event id is missing");
        }
        if (!appProperties.getServiceName().equals(event.getServiceName())) {
            throw new InvalidPaymentEventException("Payment serviceName does not match purchase owner");
        }
        if (!purchase.getPaymentConversationId().equals(metadata.purchaseConversationId())) {
            throw new InvalidPaymentEventException("Payment conversationId does not match purchase");
        }
        if (!purchase.getCurrency().equalsIgnoreCase(event.getCurrency())) {
            throw new InvalidPaymentEventException("Payment currency does not match purchase");
        }
        if (!purchase.getUserId().equals(metadata.userId())
                || !purchase.getPackageId().equals(metadata.packageId())
                || !purchase.getPackageCode().equals(metadata.packageCode())
                || !purchase.getId().equals(metadata.purchaseId())) {
            throw new InvalidPaymentEventException("Payment metadata does not match purchase");
        }
    }

    private void markEventProcessed(PaymentCompletedEventDto event, Long purchaseId) {
        paymentEventInboxRepository.save(PaymentEventInbox.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .purchaseId(purchaseId)
                .build());
    }

    private PurchaseSummaryResponse toSummary(Purchase purchase) {
        LifecycleSnapshot lifecycle = resolveLifecycle(purchase);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime refundEligibleUntil = subscriptionRefundPolicy.refundEligibleUntil(purchase);
        boolean refundEligible = isPaidActiveSubscription(purchase)
                && subscriptionRefundPolicy.isRefundEligible(purchase, now);
        return PurchaseSummaryResponse.builder()
                .purchaseId(purchase.getId())
                .userId(purchase.getUserId())
                .packageId(purchase.getPackageId())
                .packageCode(purchase.getPackageCode())
                .packageName(purchase.getPackageName())
                .price(purchase.getPrice())
                .currency(purchase.getCurrency())
                .status(purchase.getStatus())
                .paymentMode(purchase.getPaymentMode())
                .paymentStyle(purchase.getPaymentStyle())
                .purchaseType(purchase.getPurchaseType())
                .subscriptionId(purchase.getSubscriptionId())
                .subscriptionStatus(purchase.getSubscriptionStatus())
                .billingPeriod(purchase.getBillingPeriod())
                .cancelAtPeriodEnd(purchase.isCancelAtPeriodEnd())
                .currentPeriodPaidAt(purchase.getCurrentPeriodPaidAt())
                .refundEligibleUntil(refundEligibleUntil)
                .refundEligible(refundEligible)
                .refundedAt(purchase.getRefundedAt())
                .refundStatus(purchase.getRefundStatus())
                .billingSnapshot(purchase.getBillingSnapshot())
                .installmentCount(purchase.getInstallmentCount())
                .paymentId(purchase.getPaymentId())
                .paymentConversationId(purchase.getPaymentConversationId())
                .currentPeriodConversationId(purchase.getCurrentPeriodConversationId())
                .paymentMethodId(purchase.getPaymentMethodId())
                .cardBrand(purchase.getCardBrand())
                .cardLastFour(purchase.getCardLastFour())
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .purchasedAt(purchase.getPurchasedAt())
                .daysUntilExpiry(lifecycle.daysUntilExpiry())
                .nextPaymentDueAt(lifecycle.nextPaymentDueAt())
                .paymentApproaching(lifecycle.paymentApproaching())
                .expiryApproaching(lifecycle.expiryApproaching())
                .expired(lifecycle.expired())
                .usable(lifecycle.usable())
                .products(entitlementService.getPurchaseEntitlements(purchase))
                .installments(purchaseFulfillmentService.getFulfillments(purchase.getId()))
                .build();
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        LifecycleSnapshot lifecycle = resolveLifecycle(purchase);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime refundEligibleUntil = subscriptionRefundPolicy.refundEligibleUntil(purchase);
        boolean refundEligible = isPaidActiveSubscription(purchase)
                && subscriptionRefundPolicy.isRefundEligible(purchase, now);
        return PurchaseResponse.builder()
                .id(purchase.getId())
                .userId(purchase.getUserId())
                .packageId(purchase.getPackageId())
                .packageCode(purchase.getPackageCode())
                .packageName(purchase.getPackageName())
                .price(purchase.getPrice())
                .currency(purchase.getCurrency())
                .status(purchase.getStatus())
                .paymentMode(purchase.getPaymentMode())
                .paymentStyle(purchase.getPaymentStyle())
                .purchaseType(purchase.getPurchaseType())
                .subscriptionId(purchase.getSubscriptionId())
                .subscriptionStatus(purchase.getSubscriptionStatus())
                .billingPeriod(purchase.getBillingPeriod())
                .cancelAtPeriodEnd(purchase.isCancelAtPeriodEnd())
                .currentPeriodPaidAt(purchase.getCurrentPeriodPaidAt())
                .refundEligibleUntil(refundEligibleUntil)
                .refundEligible(refundEligible)
                .refundedAt(purchase.getRefundedAt())
                .refundStatus(purchase.getRefundStatus())
                .billingSnapshot(purchase.getBillingSnapshot())
                .installmentCount(purchase.getInstallmentCount())
                .paymentId(purchase.getPaymentId())
                .paymentConversationId(purchase.getPaymentConversationId())
                .currentPeriodConversationId(purchase.getCurrentPeriodConversationId())
                .paymentMethodId(purchase.getPaymentMethodId())
                .cardBrand(purchase.getCardBrand())
                .cardLastFour(purchase.getCardLastFour())
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .purchasedAt(purchase.getPurchasedAt())
                .daysUntilExpiry(lifecycle.daysUntilExpiry())
                .nextPaymentDueAt(lifecycle.nextPaymentDueAt())
                .paymentApproaching(lifecycle.paymentApproaching())
                .expiryApproaching(lifecycle.expiryApproaching())
                .expired(lifecycle.expired())
                .usable(lifecycle.usable())
                .build();
    }

    private LifecycleSnapshot resolveLifecycle(Purchase purchase) {
        boolean usable = purchase.isUsable();
        boolean expired = purchase.isEffectivelyExpired();
        Integer daysUntilExpiry = null;
        if (purchase.getExpiresAt() != null) {
            daysUntilExpiry = (int) java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.now(),
                    purchase.getExpiresAt().toLocalDate()
            );
        }
        LocalDateTime nextPaymentDueAt = purchase.isCancelAtPeriodEnd()
                ? null
                : purchaseFulfillmentService.findNextPaymentDueAt(purchase.getId());
        LocalDateTime approachingDeadline = LocalDateTime.now().plusDays(APPROACHING_DAYS);
        boolean paymentApproaching = nextPaymentDueAt != null
                && !nextPaymentDueAt.isAfter(approachingDeadline);
        boolean expiryApproaching = usable
                && purchase.getExpiresAt() != null
                && !purchase.getExpiresAt().isAfter(approachingDeadline);
        return new LifecycleSnapshot(
                usable,
                expired,
                daysUntilExpiry,
                nextPaymentDueAt,
                paymentApproaching,
                expiryApproaching
        );
    }

    private static final int APPROACHING_DAYS = 7;

    private record LifecycleSnapshot(
            boolean usable,
            boolean expired,
            Integer daysUntilExpiry,
            LocalDateTime nextPaymentDueAt,
            boolean paymentApproaching,
            boolean expiryApproaching
    ) {
    }

    private CardSnapshot resolveCardSnapshot(Long userId, Long paymentMethodId) {
        if (paymentMethodId == null) {
            return CardSnapshot.empty();
        }
        try {
            return paymentServiceClient.getPaymentMethods(userId).stream()
                    .filter(method -> String.valueOf(paymentMethodId).equals(method.id()))
                    .findFirst()
                    .map(method -> new CardSnapshot(trimToNull(method.brand()), trimToNull(method.lastFour())))
                    .orElse(CardSnapshot.empty());
        } catch (RuntimeException exception) {
            log.warn("Kart snapshot alinamadi. userId={} paymentMethodId={}", userId, paymentMethodId, exception);
            return CardSnapshot.empty();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CardSnapshot(String brand, String lastFour) {
        static CardSnapshot empty() {
            return new CardSnapshot(null, null);
        }
    }
}
