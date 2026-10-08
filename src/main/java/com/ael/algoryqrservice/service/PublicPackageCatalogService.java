package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageAddon;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.PlanPackageItemResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageModuleResponse;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.repository.PlanPackageAddonRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicPackageCatalogService {

    private final PlanPackageRepository planPackageRepository;
    private final PlanPackageAddonRepository planPackageAddonRepository;
    private final PackagePricingService packagePricingService;

    @Transactional(readOnly = true)
    public List<PlanPackageResponse> getActivePackages() {
        return list(Boolean.TRUE);
    }

    @Transactional(readOnly = true)
    public List<PlanPackageResponse> list(Boolean active) {
        boolean enabled = active == null || active;
        List<PlanPackage> ordered = planPackageRepository.findByActiveOrderByPriceAsc(enabled);
        if (ordered.isEmpty()) {
            return List.of();
        }
        List<Long> ids = ordered.stream().map(PlanPackage::getId).toList();
        Map<Long, PlanPackage> loaded = planPackageRepository.findAllByIdWithItems(ids).stream()
                .collect(Collectors.toMap(PlanPackage::getId, Function.identity()));
        List<PlanPackage> packages = ordered.stream()
                .map(pkg -> loaded.getOrDefault(pkg.getId(), pkg))
                .toList();
        return toResponses(packages);
    }

    @Transactional(readOnly = true)
    public PlanPackageResponse getById(Long id) {
        PlanPackage planPackage = planPackageRepository.findByIdWithItems(id)
                .orElseThrow(() -> new BadRequestException("Paket bulunamadi: " + id));
        return toResponse(planPackage, modulesFor(List.of(planPackage.getId())).getOrDefault(id, List.of()));
    }

    private List<PlanPackageResponse> toResponses(List<PlanPackage> packages) {
        Map<Long, List<PlanPackageModuleResponse>> modules = modulesFor(
                packages.stream().map(PlanPackage::getId).toList()
        );
        return packages.stream()
                .map(pkg -> toResponse(pkg, modules.getOrDefault(pkg.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<PlanPackageModuleResponse>> modulesFor(List<Long> packageIds) {
        return planPackageAddonRepository.findActiveByPackageIdIn(packageIds).stream()
                .collect(Collectors.groupingBy(
                        addon -> addon.getPlanPackage().getId(),
                        Collectors.mapping(this::toModuleResponse, Collectors.toList())
                ));
    }

    private PlanPackageModuleResponse toModuleResponse(PlanPackageAddon addon) {
        Product product = addon.getProduct();
        return PlanPackageModuleResponse.builder()
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .description(product.getDescription())
                .featureCode(product.getFeatureCode())
                .unitPrice(product.getUnitPrice())
                .vatRate(product.getVatRate())
                .billingType(product.getBillingType())
                .consumable(product.isConsumable())
                .build();
    }

    private PlanPackageResponse toResponse(PlanPackage planPackage, List<PlanPackageModuleResponse> availableModules) {
        PackagePricingService.PriceBreakdown breakdown = packagePricingService.calculate(planPackage.getItems());
        Map<Long, PackagePricingService.LinePrice> lineByProductId = breakdown.lines().stream()
                .collect(Collectors.toMap(PackagePricingService.LinePrice::productId, Function.identity(), (left, right) -> left));
        return PlanPackageResponse.builder()
                .id(planPackage.getId())
                .code(planPackage.getCode())
                .name(planPackage.getName())
                .description(planPackage.getDescription())
                .features(planPackage.getFeatures() == null ? List.of() : List.copyOf(planPackage.getFeatures()))
                .subtotal(planPackage.getSubtotal() != null ? planPackage.getSubtotal() : breakdown.subtotal())
                .vatAmount(planPackage.getVatAmount() != null ? planPackage.getVatAmount() : breakdown.vatAmount())
                .price(planPackage.getPrice())
                .monthlyDiscount(moneyOrZero(planPackage.getMonthlyDiscount()))
                .yearlyPrice(planPackage.getYearlyPrice())
                .yearlyDiscount(moneyOrZero(planPackage.getYearlyDiscount()))
                .effectiveMonthlyPrice(planPackage.effectiveMonthlyPrice())
                .effectiveYearlyPrice(planPackage.effectiveYearlyPrice())
                .currency(planPackage.getCurrency())
                .active(planPackage.isActive())
                .validityDays(planPackage.getValidityDays())
                .priority(planPackage.getPriority())
                .purchasable(planPackage.isPurchasable())
                .systemManaged(planPackage.isSystemManaged())
                .allowedPaymentModes(List.of(PaymentMode.CHECKOUT_FORM))
                .allowedInstallments(List.of())
                .installmentOptions(List.of())
                .items(planPackage.getItems().stream()
                        .map(item -> toItemResponse(item, lineByProductId.get(item.getProduct().getId())))
                        .toList())
                .availableModules(availableModules)
                .createdAt(planPackage.getCreatedAt())
                .build();
    }

    private PlanPackageItemResponse toItemResponse(PlanPackageItem item, PackagePricingService.LinePrice line) {
        Product product = item.getProduct();
        return PlanPackageItemResponse.builder()
                .id(item.getId())
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .quantity(item.getQuantity())
                .unlimited(item.isUnlimited())
                .unitPrice(line == null ? product.getUnitPrice() : line.unitPrice())
                .vatRate(line == null ? product.getVatRate() : line.vatRate())
                .billableQuantity(line == null ? null : line.billableQuantity())
                .lineSubtotal(line == null ? null : line.lineSubtotal())
                .lineVat(line == null ? null : line.lineVat())
                .lineTotal(line == null ? null : line.lineTotal())
                .build();
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value;
    }
}
