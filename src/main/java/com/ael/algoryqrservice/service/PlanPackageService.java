package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.*;
import com.ael.algoryqrservice.model.enums.PackageCode;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PlanPackageService {

    private final PlanPackageRepository planPackageRepository;
    private final ProductService productService;

    @Transactional
    public PlanPackageResponse create(PlanPackageRequest request) {
        rejectFreePackageMutation(request.getCode());
        validateUniqueCode(request.getCode());
        validatePackageDefinition(request);

        PlanPackage planPackage = PlanPackage.builder()
                .code(request.getCode())
                .name(request.getName().trim())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(resolveCurrency(request.getCurrency()))
                .active(request.getActive())
                .validityDays(request.getValidityDays())
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
        rejectFreePackageMutation(planPackage.getCode());

        if (planPackage.getCode() != request.getCode()) {
            validateUniqueCode(request.getCode());
        }
        validatePackageDefinition(request);

        planPackage.setCode(request.getCode());
        planPackage.setName(request.getName().trim());
        planPackage.setDescription(request.getDescription());
        planPackage.setPrice(request.getPrice());
        planPackage.setCurrency(resolveCurrency(request.getCurrency()));
        planPackage.setActive(request.getActive());
        planPackage.setValidityDays(request.getValidityDays());

        planPackage.getItems().clear();
        planPackage.getItems().addAll(buildItems(planPackage, request.getItems()));

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

    private void validateUniqueCode(PackageCode code) {
        if (planPackageRepository.existsByCode(code)) {
            throw new BadRequestException("Bu paket kodu zaten mevcut: " + code);
        }
    }

    private void rejectFreePackageMutation(PackageCode code) {
        if (code == PackageCode.FREE_PACKAGE) {
            throw new BadRequestException("FREE_PACKAGE sistem tarafından yönetilir");
        }
    }

    private void validatePackageDefinition(PlanPackageRequest request) {
        if (request.getCode() != PackageCode.PRO_PACKAGE) {
            return;
        }
        Set<ProductCode> required = Set.of(
                ProductCode.QR_CREATE,
                ProductCode.QR_MENU,
                ProductCode.QR_AGENT,
                ProductCode.QR_ANALYTICS
        );
        Set<ProductCode> actual = request.getItems().stream()
                .map(item -> productService.findActiveProduct(item.getProductId()).getCode())
                .collect(java.util.stream.Collectors.toSet());
        boolean allUnlimited = request.getItems().stream().allMatch(item -> Boolean.TRUE.equals(item.getUnlimited()));
        if (!actual.equals(required) || !allUnlimited) {
            throw new BadRequestException(
                    "PRO_PACKAGE sınırsız QR_CREATE, QR_MENU, QR_AGENT ve QR_ANALYTICS ürünlerini içermelidir"
            );
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
                    return PlanPackageItem.builder()
                            .planPackage(planPackage)
                            .product(product)
                            .quantity(itemRequest.getQuantity())
                            .unlimited(Boolean.TRUE.equals(itemRequest.getUnlimited()))
                            .build();
                })
                .toList();
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
                .allowedPaymentModes(List.of(PaymentMode.DIRECT, PaymentMode.THREE_DS))
                .allowedInstallments(allowedInstallments(planPackage))
                .installmentOptions(installmentOptions(planPackage))
                .items(planPackage.getItems().stream().map(this::toItemResponse).toList())
                .createdAt(planPackage.getCreatedAt())
                .build();
    }

    private List<Integer> allowedInstallments(PlanPackage planPackage) {
        if (planPackage.getCode() == PackageCode.FREE_PACKAGE) {
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
