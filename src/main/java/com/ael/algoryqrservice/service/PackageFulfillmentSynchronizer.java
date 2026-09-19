package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import com.ael.algoryqrservice.model.enums.ProductType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PackageFulfillmentSynchronizer {

    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final PlanPackageRepository planPackageRepository;

    @Transactional
    public int appendMissingProducts(Long userId) {
        if (userId == null) {
            return 0;
        }
        int added = 0;
        List<GrantFulfillment> active = grantFulfillmentRepository.findByUserIdAndStatus(
                userId, GrantFulfillmentStatus.ACTIVE);
        for (GrantFulfillment fulfillment : active) {
            added += appendMissingProducts(fulfillment);
        }
        return added;
    }

    int appendMissingProducts(GrantFulfillment fulfillment) {
        if (fulfillment.getPackageId() == null) {
            return 0;
        }
        List<FulfillmentDetail> details = fulfillmentDetailRepository.findByFulfillmentId(fulfillment.getId());
        if (details.stream().anyMatch(detail -> detail.getSource() == FulfillmentDetailSource.ADDON_PURCHASE)) {
            return 0;
        }
        PlanPackage planPackage = planPackageRepository.findByIdWithItems(fulfillment.getPackageId()).orElse(null);
        if (planPackage == null || planPackage.getItems() == null) {
            return 0;
        }
        return saveMissingItems(fulfillment, details, planPackage);
    }

    private int saveMissingItems(
            GrantFulfillment fulfillment,
            List<FulfillmentDetail> details,
            PlanPackage planPackage
    ) {
        FulfillmentDetailSource source = packageSource(fulfillment, details);
        Set<String> existing = new HashSet<>();
        for (FulfillmentDetail detail : details) {
            if (detail.getFeatureCode() != null) {
                existing.add(detail.getFeatureCode());
            }
        }
        int added = 0;
        for (PlanPackageItem item : planPackage.getItems()) {
            Product product = item.getProduct();
            if (product == null) {
                continue;
            }
            String featureCode = featureCodeOf(product);
            if (!existing.add(featureCode)) {
                continue;
            }
            fulfillmentDetailRepository.save(newDetail(fulfillment, product, item, source, featureCode));
            added++;
        }
        return added;
    }

    private FulfillmentDetail newDetail(
            GrantFulfillment fulfillment,
            Product product,
            PlanPackageItem item,
            FulfillmentDetailSource source,
            String featureCode
    ) {
        boolean unlimited = item.isUnlimited();
        return FulfillmentDetail.builder()
                .fulfillmentId(fulfillment.getId())
                .userId(fulfillment.getUserId())
                .productId(product.getId())
                .productTypeId(ProductType.PACKAGE_PRODUCT)
                .featureCode(featureCode)
                .scopeCode(product.getScopeCode())
                .quantity(unlimited ? 0 : item.getQuantity())
                .unlimited(unlimited)
                .usedQuantity(0)
                .source(source)
                .startsAt(fulfillment.getStartsAt())
                .expiresAt(fulfillment.getExpiresAt())
                .build();
    }

    private FulfillmentDetailSource packageSource(GrantFulfillment fulfillment, List<FulfillmentDetail> details) {
        for (FulfillmentDetail detail : details) {
            if (detail.getSource() == FulfillmentDetailSource.ONBOARDING_PACKAGE) {
                return FulfillmentDetailSource.ONBOARDING_PACKAGE;
            }
        }
        if (fulfillment.getTrialLogId() != null) {
            return FulfillmentDetailSource.ONBOARDING_PACKAGE;
        }
        return FulfillmentDetailSource.PACKAGE_INCLUDE;
    }

    private String featureCodeOf(Product product) {
        if (product.getFeatureCode() != null && !product.getFeatureCode().isBlank()) {
            return product.getFeatureCode();
        }
        return product.getCode();
    }
}
