package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.InstallmentOptionResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageItemRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageItemResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlanPackageService {

    private final PlanPackageRepository planPackageRepository;
    private final ProductService productService;

    @Transactional
    public PlanPackageResponse create(PlanPackageRequest request) {
        String code = normalizeCode(request.getCode());
        rejectSystemManagedMutation(code);
        validateUniqueCode(code);
        validatePackageItems(request.getItems());

        PlanPackage planPackage = PlanPackage.builder()
                .code(code)
                .name(request.getName().trim())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(resolveCurrency(request.getCurrency()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .validityDays(request.getValidityDays())
                .priority(request.getPriority())
                .purchasable(Boolean.TRUE.equals(request.getPurchasable()))
                .systemManaged(false)
                .trialEligible(Boolean.TRUE.equals(request.getTrialEligible()))
                .build();

        planPackage.setItems(buildItems(planPackage, request.getItems()));
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
        rejectSystemManagedMutation(planPackage);
        String code = normalizeCode(request.getCode());
        rejectSystemManagedMutation(code);

        if (!planPackage.getCode().equals(code)) {
            validateUniqueCode(code);
        }
        validatePackageItems(request.getItems());

        planPackage.setCode(code);
        planPackage.setName(request.getName().trim());
        planPackage.setDescription(request.getDescription());
        planPackage.setPrice(request.getPrice());
        planPackage.setCurrency(resolveCurrency(request.getCurrency()));
        planPackage.setActive(Boolean.TRUE.equals(request.getActive()));
        planPackage.setValidityDays(request.getValidityDays());
        planPackage.setPriority(request.getPriority());
        planPackage.setPurchasable(Boolean.TRUE.equals(request.getPurchasable()));
        planPackage.setTrialEligible(Boolean.TRUE.equals(request.getTrialEligible()));

        planPackage.getItems().clear();
        planPackage.getItems().addAll(buildItems(planPackage, request.getItems()));

        return toResponse(planPackageRepository.save(planPackage));
    }

    @Transactional
    public PlanPackageResponse updateActiveStatus(Long id, boolean active) {
        PlanPackage planPackage = findPackage(id);
        rejectSystemManagedMutation(planPackage);
        planPackage.setActive(active);
        return toResponse(planPackageRepository.save(planPackage));
    }

    PlanPackage findActivePackage(Long id) {
        PlanPackage planPackage = findPackage(id);
        if (!planPackage.isActive()) {
            throw new BadRequestException("Paket aktif değil: " + id);
        }
        if (planPackage.getItems().isEmpty()) {
            throw new BadRequestException("Paket içinde ürün bulunmuyor: " + id);
        }
        return planPackage;
    }

    PlanPackage findPackage(Long id) {
        return planPackageRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Paket bulunamadı: " + id));
    }

    private void validateUniqueCode(String code) {
        if (planPackageRepository.existsByCode(code)) {
            throw new BadRequestException("Bu paket kodu zaten mevcut: " + code);
        }
    }

    private void rejectSystemManagedMutation(PlanPackage planPackage) {
        if (planPackage.isSystemManaged() || CatalogPackages.FREE_PACKAGE.equals(planPackage.getCode())) {
            throw new BadRequestException("Sistem paketi yönetilemez: " + planPackage.getCode());
        }
    }

    private void rejectSystemManagedMutation(String code) {
        if (CatalogPackages.FREE_PACKAGE.equals(code)) {
            throw new BadRequestException("FREE_PACKAGE sistem tarafından yönetilir");
        }
    }

    private void validatePackageItems(List<PlanPackageItemRequest> items) {
        for (PlanPackageItemRequest item : items) {
            boolean unlimited = Boolean.TRUE.equals(item.getUnlimited());
            if (!unlimited && (item.getQuantity() == null || item.getQuantity() < 1)) {
                throw new BadRequestException("Sınırsız olmayan ürün için miktar en az 1 olmalıdır");
            }
        }
    }

    private List<PlanPackageItem> buildItems(PlanPackage planPackage, List<PlanPackageItemRequest> itemRequests) {
        Set<Long> productIds = new HashSet<>();
        return itemRequests.stream()
                .map(itemRequest -> {
                    if (!productIds.add(itemRequest.getProductId())) {
                        throw new BadRequestException("Aynı ürün pakete birden fazla kez eklenemez: " + itemRequest.getProductId());
                    }
                    Product product = productService.findActiveProduct(itemRequest.getProductId());
                    boolean unlimited = Boolean.TRUE.equals(itemRequest.getUnlimited());
                    return PlanPackageItem.builder()
                            .planPackage(planPackage)
                            .product(product)
                            .quantity(unlimited ? 0 : itemRequest.getQuantity())
                            .unlimited(unlimited)
                            .build();
                })
                .toList();
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private String resolveCurrency(String currency) {
        return currency == null || currency.isBlank() ? "TRY" : currency.trim().toUpperCase();
    }

    private PlanPackageResponse toResponse(PlanPackage planPackage) {
        return PlanPackageResponse.builder()
                .id(planPackage.getId())
                .code(planPackage.getCode())
                .name(planPackage.getName())
                .description(planPackage.getDescription())
                .price(planPackage.getPrice())
                .currency(planPackage.getCurrency())
                .active(planPackage.isActive())
                .validityDays(planPackage.getValidityDays())
                .priority(planPackage.getPriority())
                .purchasable(planPackage.isPurchasable())
                .systemManaged(planPackage.isSystemManaged())
                .trialEligible(planPackage.isTrialEligible())
                .allowedPaymentModes(List.of(PaymentMode.DIRECT, PaymentMode.THREE_DS))
                .allowedInstallments(allowedInstallments(planPackage))
                .installmentOptions(installmentOptions(planPackage))
                .items(planPackage.getItems().stream().map(this::toItemResponse).toList())
                .createdAt(planPackage.getCreatedAt())
                .build();
    }

    private List<Integer> allowedInstallments(PlanPackage planPackage) {
        if (!planPackage.isPurchasable() || CatalogPackages.FREE_PACKAGE.equals(planPackage.getCode())) {
            return List.of(1);
        }
        return List.of(1, 2, 3, 6, 9, 12).stream()
                .filter(count -> planPackage.getPrice().movePointRight(2)
                        .remainder(java.math.BigDecimal.valueOf(count))
                        .signum() == 0)
                .toList();
    }

    private List<InstallmentOptionResponse> installmentOptions(PlanPackage planPackage) {
        return allowedInstallments(planPackage).stream()
                .map(count -> InstallmentOptionResponse.builder()
                        .installmentCount(count)
                        .monthlyAmount(planPackage.getPrice().divide(
                                java.math.BigDecimal.valueOf(count),
                                2,
                                RoundingMode.UNNECESSARY
                        ))
                        .totalAmount(planPackage.getPrice())
                        .build())
                .toList();
    }

    private PlanPackageItemResponse toItemResponse(PlanPackageItem item) {
        return PlanPackageItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productCode(item.getProduct().getCode())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unlimited(item.isUnlimited())
                .build();
    }
}
