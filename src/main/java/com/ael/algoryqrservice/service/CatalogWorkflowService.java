package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.dto.PlanPackageItemRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.dto.ProductRequest;
import com.ael.algoryqrservice.model.dto.ProductResponse;
import com.ael.algoryqrservice.model.dto.SellablePackageComposeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogWorkflowService {

    private final ProductService productService;
    private final PlanPackageService planPackageService;

    @Transactional
    public PlanPackageResponse createSellablePackage(SellablePackageComposeRequest request) {
        List<PlanPackageItemRequest> packageItems = new ArrayList<>();

        for (SellablePackageComposeRequest.ComposeItemRequest item : request.getItems()) {
            Long productId = resolveProductId(item);
            PlanPackageItemRequest packageItem = new PlanPackageItemRequest();
            packageItem.setProductId(productId);
            packageItem.setUnlimited(item.resolvedUnlimited());
            packageItem.setQuantity(item.resolvedUnlimited() ? 0 : item.getQuantity());
            packageItems.add(packageItem);
        }

        PlanPackageRequest packageRequest = new PlanPackageRequest();
        packageRequest.setCode(request.getPackageCode());
        packageRequest.setName(request.getPackageName());
        packageRequest.setDescription(request.getDescription());
        packageRequest.setFeatures(request.getFeatures());
        packageRequest.setCurrency(request.getCurrency());
        packageRequest.setValidityDays(request.resolvedValidityDays());
        packageRequest.setPriority(request.getPriority());
        packageRequest.setActive(true);
        packageRequest.setPurchasable(true);
        packageRequest.setTrialEligible(Boolean.TRUE.equals(request.getTrialEligible()));
        packageRequest.setPrice(request.getMonthlyPrice());
        packageRequest.setMonthlyDiscount(request.getMonthlyDiscount());
        packageRequest.setYearlyPrice(request.getYearlyPrice());
        packageRequest.setYearlyDiscount(request.getYearlyDiscount());
        packageRequest.setItems(packageItems);

        PlanPackageResponse response = planPackageService.create(packageRequest);
        if (response.getEffectiveMonthlyPrice() == null || response.getEffectiveMonthlyPrice().signum() <= 0) {
            throw new BadRequestException("Paket aylik fiyati 0; aylik fiyat veya urun birim fiyatlarini girin");
        }
        return response;
    }

    private Long resolveProductId(SellablePackageComposeRequest.ComposeItemRequest item) {
        if (item.getProductId() != null) {
            return item.getProductId();
        }
        if (item.getProductName() == null || item.getProductName().isBlank()) {
            throw new BadRequestException("Urun icin productId veya productName gerekli");
        }
        if (!item.resolvedUnlimited() && (item.getQuantity() == null || item.getQuantity() < 1)) {
            throw new BadRequestException("Yeni urun icin miktar en az 1 olmalidir: " + item.getProductName());
        }

        ProductRequest productRequest = new ProductRequest();
        productRequest.setName(item.getProductName());
        productRequest.setDescription(item.getProductDescription());
        productRequest.setCountable(item.resolvedCountable());
        productRequest.setUnitPrice(item.getUnitPrice());
        productRequest.setVatRate(item.getVatRate());
        productRequest.setActive(true);
        ProductResponse created = productService.create(productRequest);
        return created.getId();
    }
}
