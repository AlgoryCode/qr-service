package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
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
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.dto.PaymentEventMetadata;
import com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.model.dto.PurchaseFulfillmentResponse;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.FulfillmentStatus;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PurchaseCancellationReason;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PaymentEventInboxRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.List;

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
    private final UserPackageService userPackageService;
    private final PurchaseFulfillmentService purchaseFulfillmentService;

    @Transactional
    public PurchaseInitiateResponse purchase(User user, PurchaseRequest request, String clientIp) {
        PlanPackage planPackage = planPackageService.findActivePackage(request.getPackageId());
        if (planPackage.getCode() == PackageCode.FREE_PACKAGE) {
            throw new BadRequestException("FREE_PACKAGE satın alınamaz");
        }
        if (!request.isPaymentPlanValid()) {
            throw new BadRequestException("Geçersiz ödeme planı");
        }
        if (planPackage.getPrice().movePointRight(2)
                .remainder(java.math.BigDecimal.valueOf(request.getInstallmentCount()))
                .signum() != 0) {
            throw new BadRequestException("Paket fiyatı seçilen taksit sayısına eşit bölünemiyor");
        }

        purchaseLogService.log(
                null,
                user.getId(),
                PurchaseLogAction.PURCHASE_STARTED,
                planPackage.getName() + " paketi satın alma başlatıldı"
        );

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .userId(user.getId())
                .packageId(planPackage.getId())
                .packageCode(planPackage.getCode())
                .packageName(planPackage.getName())
                .price(planPackage.getPrice())
                .currency(planPackage.getCurrency())
                .paymentMode(request.getPaymentMode())
                .installmentCount(request.getInstallmentCount())
                .status(PurchaseStatus.PENDING)
                .build());

        purchase.setPaymentConversationId(paymentRequestMapper.buildConversationId(purchase.getId()));
        purchaseRepository.save(purchase);
        if (request.getInstallmentCount() > 1) {
            purchaseFulfillmentService.initializeSchedule(purchase, appProperties.getServiceName());
        }

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

        if (purchase.getStatus() == PurchaseStatus.PENDING) {
            purchase.setStatus(PurchaseStatus.FAILED);
            if (event.getPaymentId() != null) {
                purchase.setPaymentId(event.getPaymentId());
            }
            purchaseRepository.save(purchase);
            purchaseLogService.log(
                    purchase.getId(),
                    purchase.getUserId(),
                    PurchaseLogAction.PURCHASE_PAYMENT_FAILED,
                    purchase.getPackageName() + " paketi ödemesi başarısız"
            );
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
        purchaseFulfillmentService.revokeInstallment(purchase, event, metadata);
        markEventProcessed(event, purchase.getId());
    }

    @Transactional
    public void cancelExpiredPendingPurchases(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Purchase> pendingPurchases = purchaseRepository.findByStatusAndPurchasedAtBefore(
                PurchaseStatus.PENDING,
                threshold
        );

        for (Purchase purchase : pendingPurchases) {
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

    @Transactional(readOnly = true)
    public List<PurchaseResponse> getUserPurchases(Long userId) {
        return purchaseRepository.findByUserIdOrderByPurchasedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseSummaryResponse getPurchaseSummary(Long purchaseId, Long userId) {
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

        entitlementService.expirePurchase(purchase);
        userPackageService.ensureFreePackage(purchase.getUserId());
        return toResponse(purchaseRepository.findById(purchaseId).orElseThrow());
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
        int installmentCount = purchase.getInstallmentCount();
        int installmentNumber = metadata.installmentNumber();
        if (installmentNumber < 1
                || installmentNumber > installmentCount
                || !metadata.installmentCount().equals(installmentCount)) {
            throw new InvalidPaymentEventException("Installment metadata does not match purchase");
        }
        var standardAmount = purchase.getPrice().divide(
                java.math.BigDecimal.valueOf(installmentCount),
                2,
                RoundingMode.DOWN
        );
        var expectedAmount = installmentNumber == installmentCount
                ? purchase.getPrice().subtract(standardAmount.multiply(
                        java.math.BigDecimal.valueOf(installmentCount - 1L)
                ))
                : standardAmount;
        if (expectedAmount.compareTo(event.getAmount()) != 0) {
            throw new InvalidPaymentEventException("Payment amount does not match purchase installment");
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
                || purchase.getPackageCode() != metadata.packageCode()
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
                .installmentCount(purchase.getInstallmentCount())
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .purchasedAt(purchase.getPurchasedAt())
                .expired(!purchase.isUsable())
                .usable(purchase.isUsable())
                .products(entitlementService.getPurchaseEntitlements(purchase))
                .installments(purchaseFulfillmentService.getFulfillments(purchase.getId()))
                .build();
    }

    private PurchaseResponse toResponse(Purchase purchase) {
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
                .installmentCount(purchase.getInstallmentCount())
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .purchasedAt(purchase.getPurchasedAt())
                .expired(!purchase.isUsable())
                .usable(purchase.isUsable())
                .build();
    }
}
