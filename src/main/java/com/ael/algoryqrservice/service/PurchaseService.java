package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.ProcessedPaymentEvent;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.ProcessedPaymentEventRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final ProcessedPaymentEventRepository processedPaymentEventRepository;

    @Transactional
    public PurchaseInitiateResponse purchase(User user, PurchaseRequest request, String clientIp) {
        PlanPackage planPackage = planPackageService.findActivePackage(request.getPackageId());

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
                .status(PurchaseStatus.PENDING)
                .build());

        purchase.setPaymentConversationId(paymentRequestMapper.buildConversationId(purchase.getId()));
        purchaseRepository.save(purchase);

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
            PaymentThreeDsResponse paymentResponse = paymentServiceClient.initializeThreeDsPayment(paymentRequest);

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
        if (processedPaymentEventRepository.existsByEventId(event.getEventId())) {
            return;
        }

        Long purchaseId = parsePurchaseId(event);
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

        if (purchase.getStatus() == PurchaseStatus.ACTIVE) {
            markEventProcessed(event.getEventId(), purchaseId);
            return;
        }

        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            log.warn("Payment success ignored for purchaseId={} status={}", purchaseId, purchase.getStatus());
            markEventProcessed(event.getEventId(), purchaseId);
            return;
        }

        PlanPackage planPackage = planPackageService.findActivePackage(purchase.getPackageId());
        LocalDateTime startsAt = LocalDateTime.now();
        LocalDateTime expiresAt = startsAt.plusDays(planPackage.getValidityDays());

        purchase.setStatus(PurchaseStatus.ACTIVE);
        purchase.setPaymentId(event.getPaymentId());
        purchase.setStartsAt(startsAt);
        purchase.setExpiresAt(expiresAt);
        purchaseRepository.save(purchase);

        for (PlanPackageItem item : planPackage.getItems()) {
            entitlementService.grant(
                    purchase,
                    item.getProduct().getId(),
                    item.getProduct().getCode(),
                    item.getQuantity()
            );
        }

        purchaseLogService.log(
                purchase.getId(),
                purchase.getUserId(),
                PurchaseLogAction.PURCHASE_COMPLETED,
                purchase.getPackageName() + " paketi satın alma tamamlandı ("
                        + startsAt + " - " + expiresAt + ")"
        );

        markEventProcessed(event.getEventId(), purchaseId);
    }

    @Transactional
    public void handlePaymentFailed(PaymentCompletedEventDto event) {
        if (processedPaymentEventRepository.existsByEventId(event.getEventId())) {
            return;
        }

        Long purchaseId = parsePurchaseId(event);
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BadRequestException("Satın alım bulunamadı: " + purchaseId));

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

        markEventProcessed(event.getEventId(), purchaseId);
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

    private Long parsePurchaseId(PaymentCompletedEventDto event) {
        if (event.getSourceReferenceId() != null && !event.getSourceReferenceId().isBlank()) {
            return Long.parseLong(event.getSourceReferenceId());
        }
        Map<String, Object> metadata = event.getSourceMetadata();
        if (metadata != null && metadata.get("purchaseId") != null) {
            return Long.parseLong(String.valueOf(metadata.get("purchaseId")));
        }
        throw new BadRequestException("Ödeme event'inde purchaseId bulunamadı");
    }

    private void markEventProcessed(String eventId, Long purchaseId) {
        processedPaymentEventRepository.save(ProcessedPaymentEvent.builder()
                .eventId(eventId)
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
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .purchasedAt(purchase.getPurchasedAt())
                .expired(!purchase.isUsable())
                .usable(purchase.isUsable())
                .products(entitlementService.getPurchaseEntitlements(purchase))
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
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .purchasedAt(purchase.getPurchasedAt())
                .expired(!purchase.isUsable())
                .usable(purchase.isUsable())
                .build();
    }
}
