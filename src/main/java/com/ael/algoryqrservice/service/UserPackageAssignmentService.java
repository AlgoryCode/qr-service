package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.PackageView;
import com.ael.algoryqrservice.client.dto.AssignedProduct;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.ConflictException;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPackageAssignmentService {

    public static final String ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
    public static final String PACKAGE_EXISTS = "PACKAGE_EXISTS";
    public static final String ACCOUNT_IN_DEBT = "ACCOUNT_IN_DEBT";
    public static final String PACKAGE_PRICE_MISMATCH = "PACKAGE_PRICE_MISMATCH";

    private static final String ACTIVE = "ACTIVE";
    private static final String CURRENCY = "TRY";
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final ObjectProvider<FulfillmentServiceClient> fulfillmentServiceClient;
    private final AuthMerchantClient authMerchantClient;
    private final UserDebtService userDebtService;
    private final PlanPackageRepository planPackageRepository;
    private final PackagePricingService packagePricingService;

    public PackageControl read(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            throw new BadRequestException("İşletme geçersiz");
        }
        PackageView view = client().findPackage(merchantId);
        if (view.body() == null) {
            return new PackageControl("NONE", null, null);
        }
        String periodEnd = view.body().periodEnd() == null ? null : view.body().periodEnd().toString();
        return new PackageControl(view.status(), view.body().packageCode(), periodEnd);
    }

    public void create(Long merchantId, Long packageId, String packageCode) {
        if (merchantId == null || merchantId <= 0) {
            throw new BadRequestException("İşletme geçersiz");
        }
        AuthMerchantClient.MerchantView merchant = authMerchantClient.findMerchant(merchantId);
        if (!ACTIVE.equals(merchant.status())) {
            throw new BadRequestException(ACCOUNT_INACTIVE, "Hesap aktif değil");
        }
        PackageView current = client().findPackage(merchantId);
        if (ACTIVE.equals(current.status())) {
            throw new ConflictException(PACKAGE_EXISTS, "Aktif paket mevcut");
        }
        if (userDebtService.findDebt(merchantId).isPresent()) {
            throw new BadRequestException(ACCOUNT_IN_DEBT, "Hesap borcu var");
        }
        Long resolvedId = resolvePackageId(packageId, packageCode);
        PlanPackage plan = planPackageRepository.findByIdWithItems(resolvedId)
                .orElseThrow(() -> new NotFoundException("Paket bulunamadı"));
        requireCatalog(plan);
        if (!CatalogPackages.ULTIMATE_TRIAL_PACKAGE.equals(plan.getCode())) {
            requirePrice(plan, packagePricingService.calculate(plan.getItems()));
        }
        client().createPackage(
                merchantId,
                plan.getCode(),
                plan.getValidityDays(),
                assignedItems(plan),
                List.of()
        );
    }

    private List<AssignedProduct> assignedItems(PlanPackage plan) {
        if (plan.getItems() == null) {
            return List.of();
        }
        return plan.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .map(item -> new AssignedProduct(
                        item.getProduct().getCode(),
                        item.getProduct().getName(),
                        item.getQuantity() == null ? 0 : item.getQuantity(),
                        item.isUnlimited()
                ))
                .toList();
    }

    private Long resolvePackageId(Long packageId, String packageCode) {
        if (packageId != null) {
            return packageId;
        }
        if (packageCode == null || packageCode.isBlank()) {
            throw new BadRequestException("Paket zorunludur");
        }
        return planPackageRepository.findByCode(packageCode.trim())
                .map(PlanPackage::getId)
                .orElseThrow(() -> new NotFoundException("Paket bulunamadı"));
    }

    private void requireCatalog(PlanPackage plan) {
        if (!plan.isActive()) {
            throw new BadRequestException("Paket satışa kapalı");
        }
        if (plan.getItems() == null || plan.getItems().isEmpty()) {
            throw new BadRequestException("Paket kalemleri boş");
        }
        boolean trial = CatalogPackages.ULTIMATE_TRIAL_PACKAGE.equals(plan.getCode());
        if (!trial && (!plan.isPurchasable() || plan.isSystemManaged())) {
            throw new BadRequestException("Paket satın alınamaz");
        }
        if (plan.getValidityDays() == null || plan.getValidityDays() < 1) {
            throw new BadRequestException("Paket süresi geçersiz");
        }
        if (plan.getCurrency() == null || !CURRENCY.equals(plan.getCurrency())) {
            throw new BadRequestException("Paket para birimi TRY olmalı");
        }
        for (PlanPackageItem item : plan.getItems()) {
            Product product = item.getProduct();
            if (product == null || !product.isActive()) {
                throw new BadRequestException("Paket ürünü aktif değil");
            }
            if (product.getUnitPrice() == null) {
                throw new BadRequestException("Ürün fiyatı boş");
            }
            if (!item.isUnlimited() && (item.getQuantity() == null || item.getQuantity() <= 0)) {
                throw new BadRequestException("Ürün adedi geçersiz");
            }
        }
    }

    private void requirePrice(PlanPackage plan, PackagePricingService.PriceBreakdown breakdown) {
        for (PackagePricingService.LinePrice line : breakdown.lines()) {
            if (!same(line.lineTotal(), line.lineSubtotal().add(line.lineVat()))) {
                throw new BadRequestException(PACKAGE_PRICE_MISMATCH, "Paket satır tutarı uyuşmuyor");
            }
        }
        if (!same(plan.getSubtotal(), breakdown.subtotal())
                || !same(plan.getVatAmount(), breakdown.vatAmount())
                || !same(plan.getPrice(), breakdown.total())) {
            throw new BadRequestException(PACKAGE_PRICE_MISMATCH, "Paket tutarı ürün toplamıyla uyuşmuyor");
        }
    }

    private boolean same(BigDecimal stored, BigDecimal calculated) {
        if (stored == null || calculated == null) {
            return false;
        }
        return stored.subtract(calculated).abs().compareTo(TOLERANCE) <= 0;
    }

    public record PackageControl(String status, String packageCode, String periodEnd) {
    }

    private FulfillmentServiceClient client() {
        FulfillmentServiceClient client = fulfillmentServiceClient.getIfAvailable();
        if (client == null) {
            throw new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor");
        }
        return client;
    }
}
