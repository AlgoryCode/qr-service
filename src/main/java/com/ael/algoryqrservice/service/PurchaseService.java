package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PlanPackageService planPackageService;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseLogService purchaseLogService;
    private final EntitlementService entitlementService;

    @Transactional
    public PurchaseResponse purchase(Long userId, PurchaseRequest request) {
        PlanPackage planPackage = planPackageService.findActivePackage(request.getPackageId());

        purchaseLogService.log(
                null,
                userId,
                PurchaseLogAction.PURCHASE_STARTED,
                planPackage.getName() + " paketi satın alma başlatıldı"
        );

        LocalDateTime startsAt = LocalDateTime.now();
        LocalDateTime expiresAt = startsAt.plusDays(planPackage.getValidityDays());

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .userId(userId)
                .packageId(planPackage.getId())
                .packageCode(planPackage.getCode())
                .packageName(planPackage.getName())
                .price(planPackage.getPrice())
                .currency(planPackage.getCurrency())
                .status(PurchaseStatus.ACTIVE)
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .build());

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
                userId,
                PurchaseLogAction.PURCHASE_COMPLETED,
                planPackage.getName() + " paketi satın alma tamamlandı ("
                        + startsAt + " - " + expiresAt + ")"
        );

        return toResponse(purchase);
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
