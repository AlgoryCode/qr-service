package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.*;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        validateUniqueCode(request.getCode());

        PlanPackage planPackage = PlanPackage.builder()
                .code(request.getCode().trim().toUpperCase())
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

        if (!planPackage.getCode().equalsIgnoreCase(request.getCode())) {
            validateUniqueCode(request.getCode());
        }

        planPackage.setCode(request.getCode().trim().toUpperCase());
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

    private PlanPackage findPackage(Long id) {
        return planPackageRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Paket bulunamadı: " + id));
    }

    private void validateUniqueCode(String code) {
        if (planPackageRepository.existsByCode(code.trim().toUpperCase())) {
            throw new BadRequestException("Bu paket kodu zaten mevcut: " + code);
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
                .items(planPackage.getItems().stream().map(this::toItemResponse).toList())
                .createdAt(planPackage.getCreatedAt())
                .build();
    }

    private PlanPackageItemResponse toItemResponse(PlanPackageItem item) {
        return PlanPackageItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productCode(item.getProduct().getCode())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .build();
    }
}
