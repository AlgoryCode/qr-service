package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogCodeFactory;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.PlanPackageItemRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageItemResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.dto.PublishPackageRequest;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanPackageService {

    private final PlanPackageRepository planPackageRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductService productService;
    private final CatalogCodeFactory catalogCodeFactory;
    private final PackagePricingService packagePricingService;

    @Transactional
    public PlanPackageResponse create(PlanPackageRequest request) {
        String code = catalogCodeFactory.resolveUnique(
                request.getCode(),
                request.getName(),
                planPackageRepository::existsByCode
        );
        rejectSystemManagedMutation(code);

        List<PlanPackageItemRequest> items = request.getItems() == null ? List.of() : request.getItems();
        if (request.resolvedPurchasable() && items.isEmpty()) {
            throw new BadRequestException("Satilabilir paket en az bir urun icermelidir");
        }
        validatePackageItems(items);

        PlanPackage planPackage = PlanPackage.builder()
                .code(code)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .features(normalizeFeatures(request.getFeatures()))
                .price(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .vatAmount(BigDecimal.ZERO)
                .currency(resolveCurrency(request.getCurrency()))
                .active(request.resolvedActive())
                .validityDays(request.resolvedValidityDays())
                .priority(request.resolvedPriority())
                .purchasable(request.resolvedPurchasable())
                .systemManaged(false)
                .trialEligible(request.resolvedTrialEligible())
                .items(new ArrayList<>())
                .build();

        planPackage.getItems().addAll(buildItems(planPackage, items));
        packagePricingService.applyTo(planPackage);
        applySubscriptionPricing(planPackage, request);
        return toResponse(planPackageRepository.save(planPackage));
    }

    @Transactional(readOnly = true)
    public List<PlanPackageResponse> getAll() {
        return planPackageRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanPackageResponse> getActivePackages() {
        return planPackageRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanPackageResponse getById(Long id) {
        return toResponse(findPackage(id));
    }

    @Transactional
    public PlanPackageResponse update(Long id, PlanPackageRequest request) {
        PlanPackage planPackage = findPackage(id);

        List<PlanPackageItemRequest> items = request.getItems() == null ? List.of() : request.getItems();
        boolean purchasable = request.resolvedPurchasable();
        if (purchasable && items.isEmpty()) {
            throw new BadRequestException("Satilabilir paket en az bir urun icermelidir");
        }
        validatePackageItems(items);

        planPackage.setName(request.getName().trim());
        planPackage.setDescription(trimToNull(request.getDescription()));
        planPackage.setFeatures(normalizeFeatures(request.getFeatures()));
        planPackage.setCurrency(resolveCurrency(request.getCurrency()));
        planPackage.setActive(request.resolvedActive());
        planPackage.setValidityDays(request.resolvedValidityDays());
        planPackage.setPriority(request.resolvedPriority());
        planPackage.setPurchasable(purchasable);
        planPackage.setTrialEligible(request.resolvedTrialEligible());

        syncItems(planPackage, items);
        packagePricingService.applyTo(planPackage);
        applySubscriptionPricing(planPackage, request);

        return toResponse(planPackageRepository.save(planPackage));
    }

    @Transactional
    public PlanPackageResponse addItem(Long packageId, PlanPackageItemRequest itemRequest) {
        PlanPackage planPackage = findPackage(packageId);
        validatePackageItems(List.of(itemRequest));

        boolean exists = planPackage.getItems().stream()
                .anyMatch(item -> item.getProduct().getId().equals(itemRequest.getProductId()));
        if (exists) {
            throw new BadRequestException("Urun zaten pakette: " + itemRequest.getProductId());
        }

        planPackage.getItems().addAll(buildItems(planPackage, List.of(itemRequest)));
        packagePricingService.applyTo(planPackage);
        return toResponse(planPackageRepository.save(planPackage));
    }

    @Transactional
    public PlanPackageResponse removeItem(Long packageId, Long productId) {
        PlanPackage planPackage = findPackage(packageId);

        boolean removed = planPackage.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        if (!removed) {
            throw new BadRequestException("Pakette urun bulunamadi: " + productId);
        }
        if (planPackage.isPurchasable() && planPackage.getItems().isEmpty()) {
            throw new BadRequestException("Satilabilir paketten son urun silinemez");
        }
        packagePricingService.applyTo(planPackage);
        return toResponse(planPackageRepository.save(planPackage));
    }

    @Transactional
    public PlanPackageResponse publish(Long id, PublishPackageRequest request) {
        PlanPackage planPackage = findPackage(id);
        if (planPackage.getItems().isEmpty()) {
            throw new BadRequestException("Yayinlamak icin pakete en az bir urun ekleyin");
        }
        packagePricingService.applyTo(planPackage);
        if (planPackage.getPrice() == null || planPackage.effectiveMonthlyPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Satilabilir paket fiyati 0'dan buyuk olmalidir; urun birim fiyatlarini veya aylik fiyati kontrol edin");
        }
        validatePurchasableSubscriptionPricing(planPackage);

        boolean purchasable = request.getPurchasable() == null || Boolean.TRUE.equals(request.getPurchasable());
        boolean active = request.getActive() == null || Boolean.TRUE.equals(request.getActive());
        planPackage.setPurchasable(purchasable);
        planPackage.setActive(active);
        if (request.getTrialEligible() != null) {
            planPackage.setTrialEligible(request.getTrialEligible());
        }
        return toResponse(planPackageRepository.save(planPackage));
    }

    @Transactional
    public PlanPackageResponse updateActiveStatus(Long id, boolean active) {
        PlanPackage planPackage = findPackage(id);
        planPackage.setActive(active);
        return toResponse(planPackageRepository.save(planPackage));
    }

    @Transactional
    public void delete(Long id) {
        PlanPackage planPackage = findPackage(id);
        if (purchaseRepository.existsByPackageIdAndStatus(id, PurchaseStatus.ACTIVE)
                || purchaseRepository.existsByPackageIdAndStatus(id, PurchaseStatus.PENDING)) {
            throw new BadRequestException(
                    "Paketin aktif veya odeme bekleyen satin alimi var; once iptal/expire edin: "
                            + planPackage.getCode()
            );
        }
        planPackageRepository.delete(planPackage);
    }

    PlanPackage findActivePackage(Long id) {
        PlanPackage planPackage = findPackage(id);
        if (!planPackage.isActive()) {
            throw new BadRequestException("Paket aktif degil: " + id);
        }
        if (planPackage.getItems().isEmpty()) {
            throw new BadRequestException("Paket icinde urun bulunmuyor: " + id);
        }
        return planPackage;
    }

    PlanPackage findPackage(Long id) {
        return planPackageRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BadRequestException("Paket bulunamadi: " + id));
    }

    private void rejectSystemManagedMutation(String code) {
        if (CatalogPackages.FREE_PACKAGE.equals(code)) {
            throw new BadRequestException("FREE_PACKAGE kodu yalnizca startup seed ile olusturulabilir");
        }
    }

    private void validatePackageItems(List<PlanPackageItemRequest> items) {
        for (PlanPackageItemRequest item : items) {
            boolean unlimited = item.resolvedUnlimited();
            if (!unlimited && (item.getQuantity() == null || item.getQuantity() < 1)) {
                throw new BadRequestException("Sinirsiz olmayan urun icin miktar en az 1 olmalidir");
            }
        }
    }

    private List<PlanPackageItem> buildItems(PlanPackage planPackage, List<PlanPackageItemRequest> itemRequests) {
        Set<Long> productIds = new HashSet<>();
        return itemRequests.stream()
                .map(itemRequest -> {
                    if (!productIds.add(itemRequest.getProductId())) {
                        throw new BadRequestException("Ayni urun pakete birden fazla kez eklenemez: " + itemRequest.getProductId());
                    }
                    Product product = productService.findActiveProduct(itemRequest.getProductId());
                    boolean unlimited = itemRequest.resolvedUnlimited();
                    return PlanPackageItem.builder()
                            .planPackage(planPackage)
                            .product(product)
                            .quantity(unlimited ? 0 : itemRequest.getQuantity())
                            .unlimited(unlimited)
                            .build();
                })
                .toList();
    }

    private void syncItems(PlanPackage planPackage, List<PlanPackageItemRequest> itemRequests) {
        Set<Long> requestedProductIds = new HashSet<>();
        for (PlanPackageItemRequest itemRequest : itemRequests) {
            if (!requestedProductIds.add(itemRequest.getProductId())) {
                throw new BadRequestException("Ayni urun pakete birden fazla kez eklenemez: " + itemRequest.getProductId());
            }
        }

        planPackage.getItems().removeIf(item -> !requestedProductIds.contains(item.getProduct().getId()));

        Map<Long, PlanPackageItem> existingByProductId = planPackage.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity(), (a, b) -> a));

        for (PlanPackageItemRequest itemRequest : itemRequests) {
            boolean unlimited = itemRequest.resolvedUnlimited();
            int quantity = unlimited ? 0 : itemRequest.getQuantity();
            PlanPackageItem existing = existingByProductId.get(itemRequest.getProductId());
            if (existing != null) {
                existing.setQuantity(quantity);
                existing.setUnlimited(unlimited);
                continue;
            }
            Product product = productService.findActiveProduct(itemRequest.getProductId());
            planPackage.getItems().add(PlanPackageItem.builder()
                    .planPackage(planPackage)
                    .product(product)
                    .quantity(quantity)
                    .unlimited(unlimited)
                    .build());
        }
    }

    private String resolveCurrency(String currency) {
        return currency == null || currency.isBlank() ? "TRY" : currency.trim().toUpperCase();
    }

    private void applySubscriptionPricing(PlanPackage planPackage, PlanPackageRequest request) {
        if (request.getPrice() != null) {
            planPackage.setPrice(request.getPrice().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.getMonthlyDiscount() != null) {
            planPackage.setMonthlyDiscount(normalizeMoney(request.getMonthlyDiscount()));
        } else if (planPackage.getMonthlyDiscount() == null) {
            planPackage.setMonthlyDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        if (request.getYearlyPrice() != null) {
            planPackage.setYearlyPrice(request.getYearlyPrice().setScale(2, RoundingMode.HALF_UP));
        } else if (planPackage.isPurchasable()
                && !CatalogPackages.FREE_PACKAGE.equals(planPackage.getCode())
                && planPackage.getYearlyPrice() == null
                && planPackage.getPrice() != null
                && planPackage.getPrice().signum() > 0) {
            planPackage.setYearlyPrice(planPackage.getPrice().multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP));
        }
        if (request.getYearlyDiscount() != null) {
            planPackage.setYearlyDiscount(normalizeMoney(request.getYearlyDiscount()));
        } else if (planPackage.getYearlyDiscount() == null) {
            planPackage.setYearlyDiscount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        if (planPackage.isPurchasable() && !CatalogPackages.FREE_PACKAGE.equals(planPackage.getCode())) {
            validatePurchasableSubscriptionPricing(planPackage);
        }
    }

    private void validatePurchasableSubscriptionPricing(PlanPackage planPackage) {
        BigDecimal monthly = planPackage.effectiveMonthlyPrice();
        if (monthly == null || monthly.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Aylik satis fiyati 0'dan buyuk olmalidir");
        }
        if (planPackage.getMonthlyDiscount() != null
                && planPackage.getPrice() != null
                && planPackage.getMonthlyDiscount().compareTo(planPackage.getPrice()) >= 0) {
            throw new BadRequestException("Aylik indirim aylik fiyattan kucuk olmalidir");
        }
        if (planPackage.getYearlyPrice() == null || planPackage.getYearlyPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Yillik fiyat zorunludur ve 0'dan buyuk olmalidir");
        }
        BigDecimal yearly = planPackage.effectiveYearlyPrice();
        if (yearly == null || yearly.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Yillik satis fiyati 0'dan buyuk olmalidir");
        }
        if (planPackage.getYearlyDiscount() != null
                && planPackage.getYearlyDiscount().compareTo(planPackage.getYearlyPrice()) >= 0) {
            throw new BadRequestException("Yillik indirim yillik fiyattan kucuk olmalidir");
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeFeatures(List<String> features) {
        if (features == null || features.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String feature : features) {
            if (feature == null) {
                continue;
            }
            String trimmed = feature.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > 200) {
                throw new BadRequestException("Ozellik metni en fazla 200 karakter olabilir");
            }
            normalized.add(trimmed);
            if (normalized.size() > 30) {
                throw new BadRequestException("En fazla 30 ozellik eklenebilir");
            }
        }
        return normalized;
    }

    private PlanPackageResponse toResponse(PlanPackage planPackage) {
        PackagePricingService.PriceBreakdown breakdown = packagePricingService.calculate(planPackage.getItems());
        Map<Long, PackagePricingService.LinePrice> lineByProductId = breakdown.lines().stream()
                .collect(Collectors.toMap(PackagePricingService.LinePrice::productId, Function.identity(), (a, b) -> a));

        return PlanPackageResponse.builder()
                .id(planPackage.getId())
                .code(planPackage.getCode())
                .name(planPackage.getName())
                .description(planPackage.getDescription())
                .features(planPackage.getFeatures() == null ? List.of() : List.copyOf(planPackage.getFeatures()))
                .subtotal(planPackage.getSubtotal() != null ? planPackage.getSubtotal() : breakdown.subtotal())
                .vatAmount(planPackage.getVatAmount() != null ? planPackage.getVatAmount() : breakdown.vatAmount())
                .price(planPackage.getPrice())
                .monthlyDiscount(planPackage.getMonthlyDiscount() == null
                        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : planPackage.getMonthlyDiscount())
                .yearlyPrice(planPackage.getYearlyPrice())
                .yearlyDiscount(planPackage.getYearlyDiscount() == null
                        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : planPackage.getYearlyDiscount())
                .effectiveMonthlyPrice(planPackage.effectiveMonthlyPrice())
                .effectiveYearlyPrice(planPackage.effectiveYearlyPrice())
                .currency(planPackage.getCurrency())
                .active(planPackage.isActive())
                .validityDays(planPackage.getValidityDays())
                .priority(planPackage.getPriority())
                .purchasable(planPackage.isPurchasable())
                .systemManaged(planPackage.isSystemManaged())
                .trialEligible(planPackage.isTrialEligible())
                .allowedPaymentModes(List.of(PaymentMode.DIRECT, PaymentMode.THREE_DS))
                .allowedInstallments(List.of())
                .installmentOptions(List.of())
                .items(planPackage.getItems().stream()
                        .map(item -> toItemResponse(item, lineByProductId.get(item.getProduct().getId())))
                        .toList())
                .createdAt(planPackage.getCreatedAt())
                .build();
    }

    private PlanPackageItemResponse toItemResponse(PlanPackageItem item, PackagePricingService.LinePrice line) {
        return PlanPackageItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productCode(item.getProduct().getCode())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unlimited(item.isUnlimited())
                .unitPrice(line == null ? item.getProduct().getUnitPrice() : line.unitPrice())
                .vatRate(line == null ? item.getProduct().getVatRate() : line.vatRate())
                .billableQuantity(line == null ? null : line.billableQuantity())
                .lineSubtotal(line == null ? null : line.lineSubtotal())
                .lineVat(line == null ? null : line.lineVat())
                .lineTotal(line == null ? null : line.lineTotal())
                .build();
    }
}
