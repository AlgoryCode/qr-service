package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import com.ael.algoryqrservice.model.enums.ProductType;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentGrantService {

    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final PlanPackageRepository planPackageRepository;
    private final ProductRepository productRepository;

    @Transactional
    public GrantFulfillment grantPackageFulfillment(Purchase purchase, PlanPackage planPackage) {
        if (purchase.getPurchaseType() == PurchaseType.ADD_ON) {
            return grantAddonFulfillment(purchase);
        }
        GrantFulfillment existing = grantFulfillmentRepository.findByPurchaseId(purchase.getId()).orElse(null);
        if (existing != null) {
            log.debug("Fulfillment already exists for purchaseId={}", purchase.getId());
            synchronizeFulfillmentPeriod(existing, purchase);
            return existing;
        }
        GrantFulfillment fulfillment = grantFulfillmentRepository.save(GrantFulfillment.builder()
                .userId(purchase.getUserId())
                .purchaseId(purchase.getId())
                .paymentId(purchase.getPaymentId())
                .packageId(purchase.getPackageId())
                .status(GrantFulfillmentStatus.ACTIVE)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build());

        PlanPackage withItems = planPackageRepository.findByIdWithItems(planPackage.getId())
                .orElse(planPackage);
        for (PlanPackageItem item : withItems.getItems()) {
            Product product = item.getProduct();
            String featureCode = resolveFeatureCode(product);
            fulfillmentDetailRepository.save(FulfillmentDetail.builder()
                    .fulfillmentId(fulfillment.getId())
                    .userId(purchase.getUserId())
                    .productId(product.getId())
                    .productTypeId(ProductType.PACKAGE_PRODUCT)
                    .featureCode(featureCode)
                    .scopeCode(product.getScopeCode())
                    .quantity(item.isUnlimited() ? 0 : item.getQuantity())
                    .unlimited(item.isUnlimited())
                    .usedQuantity(0)
                    .source(FulfillmentDetailSource.PACKAGE_INCLUDE)
                    .startsAt(purchase.getStartsAt())
                    .expiresAt(purchase.getExpiresAt())
                    .build());
        }
        log.info("Package fulfillment granted: userId={}, purchaseId={}, fulfillmentId={}",
                purchase.getUserId(), purchase.getId(), fulfillment.getId());
        return fulfillment;
    }

    @Transactional
    public GrantFulfillment grantAddonFulfillment(Purchase purchase) {
        GrantFulfillment existing = grantFulfillmentRepository.findByPurchaseId(purchase.getId()).orElse(null);
        if (existing != null) {
            log.debug("Addon fulfillment already exists for purchaseId={}", purchase.getId());
            synchronizeFulfillmentPeriod(existing, purchase);
            return existing;
        }
        Long packageId = purchase.getPackageId();
        if (packageId == null) {
            log.warn("Addon purchase has no packageId: purchaseId={}", purchase.getId());
            return null;
        }
        GrantFulfillment fulfillment = grantFulfillmentRepository.save(GrantFulfillment.builder()
                .userId(purchase.getUserId())
                .purchaseId(purchase.getId())
                .paymentId(purchase.getPaymentId())
                .packageId(packageId)
                .status(GrantFulfillmentStatus.ACTIVE)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build());

        String productCode = resolveAddonProductCode(purchase);
        Product product = productRepository.findByCode(productCode).orElse(null);
        String featureCode = product != null ? resolveFeatureCode(product) : productCode;
        String scopeCode = product != null ? product.getScopeCode() : null;
        int quantity = resolveAddonQuantity(purchase);

        fulfillmentDetailRepository.save(FulfillmentDetail.builder()
                .fulfillmentId(fulfillment.getId())
                .userId(purchase.getUserId())
                .productId(product != null ? product.getId() : null)
                .productTypeId(ProductType.ADDON_PRODUCT)
                .featureCode(featureCode)
                .scopeCode(scopeCode)
                .quantity(quantity)
                .unlimited(false)
                .usedQuantity(0)
                .source(FulfillmentDetailSource.ADDON_PURCHASE)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build());

        log.info("Addon fulfillment granted: userId={}, purchaseId={}, featureCode={}, quantity={}",
                purchase.getUserId(), purchase.getId(), featureCode, quantity);
        return fulfillment;
    }

    @Transactional
    public void supersedeFulfillmentForPurchase(Long purchaseId) {
        grantFulfillmentRepository.findByPurchaseId(purchaseId).ifPresent(f -> {
            f.setStatus(GrantFulfillmentStatus.SUPERSEDED);
            grantFulfillmentRepository.save(f);
        });
    }

    @Transactional
    public void expireFulfillmentForPurchase(Long purchaseId) {
        grantFulfillmentRepository.findByPurchaseId(purchaseId).ifPresent(f -> {
            if (f.getStatus() == GrantFulfillmentStatus.ACTIVE) {
                f.setStatus(GrantFulfillmentStatus.EXPIRED);
                grantFulfillmentRepository.save(f);
                log.info("Fulfillment expired: fulfillmentId={}, purchaseId={}", f.getId(), purchaseId);
            }
        });
    }

    @Transactional
    public void expireAddonFulfillmentsForUser(Long userId, Long packageId) {
        List<GrantFulfillment> addonFulfillments = grantFulfillmentRepository
                .findByUserIdAndStatus(userId, GrantFulfillmentStatus.ACTIVE).stream()
                .filter(f -> packageId.equals(f.getPackageId()))
                .toList();
        for (GrantFulfillment f : addonFulfillments) {
            boolean isAddon = fulfillmentDetailRepository.findByFulfillmentId(f.getId()).stream()
                    .anyMatch(d -> d.getSource() == FulfillmentDetailSource.ADDON_PURCHASE);
            if (isAddon) {
                f.setStatus(GrantFulfillmentStatus.EXPIRED);
                grantFulfillmentRepository.save(f);
                log.info("Addon fulfillment cascade-expired: fulfillmentId={}, userId={}, packageId={}",
                        f.getId(), userId, packageId);
            }
        }
    }

    @Transactional
    public void relinkAddonFulfillmentsToNewPackage(Long userId, Long oldPackageId, Long newPackageId) {
        List<GrantFulfillment> addonFulfillments = grantFulfillmentRepository
                .findByUserIdAndStatus(userId, GrantFulfillmentStatus.ACTIVE).stream()
                .filter(f -> oldPackageId.equals(f.getPackageId()))
                .toList();
        for (GrantFulfillment f : addonFulfillments) {
            boolean isAddon = fulfillmentDetailRepository.findByFulfillmentId(f.getId()).stream()
                    .anyMatch(d -> d.getSource() == FulfillmentDetailSource.ADDON_PURCHASE);
            if (isAddon) {
                f.setPackageId(newPackageId);
                grantFulfillmentRepository.save(f);
                log.info("Addon fulfillment re-linked: fulfillmentId={}, oldPkg={}, newPkg={}",
                        f.getId(), oldPackageId, newPackageId);
            }
        }
    }

    private void synchronizeFulfillmentPeriod(GrantFulfillment fulfillment, Purchase purchase) {
        if (purchase.getExpiresAt() != null && !purchase.getExpiresAt().equals(fulfillment.getExpiresAt())) {
            fulfillment.setStartsAt(purchase.getStartsAt());
            fulfillment.setExpiresAt(purchase.getExpiresAt());
            if (purchase.getPaymentId() != null) {
                fulfillment.setPaymentId(purchase.getPaymentId());
            }
            grantFulfillmentRepository.save(fulfillment);
            List<FulfillmentDetail> details = fulfillmentDetailRepository.findByFulfillmentId(fulfillment.getId());
            for (FulfillmentDetail detail : details) {
                detail.setStartsAt(purchase.getStartsAt());
                detail.setExpiresAt(purchase.getExpiresAt());
            }
            fulfillmentDetailRepository.saveAll(details);
        }
    }

    @Transactional
    public void repairAddonFulfillmentsForUser(Long userId) {
        LocalDateTime now = AppTime.nowLocal();
        List<GrantFulfillment> active = grantFulfillmentRepository.findByUserIdAndStatus(userId, GrantFulfillmentStatus.ACTIVE);
        for (GrantFulfillment fulfillment : active) {
            List<FulfillmentDetail> details = fulfillmentDetailRepository.findByFulfillmentId(fulfillment.getId());
            boolean isAddon = details.stream()
                    .anyMatch(d -> d.getSource() == FulfillmentDetailSource.ADDON_PURCHASE);
            if (!isAddon) {
                continue;
            }
            if (fulfillment.getStartsAt() != null && fulfillment.getStartsAt().isAfter(now)) {
                fulfillment.setStartsAt(now);
                grantFulfillmentRepository.save(fulfillment);
                log.info("Repaired addon GrantFulfillment startsAt: fulfillmentId={}, userId={}",
                        fulfillment.getId(), userId);
            }
            for (FulfillmentDetail detail : details) {
                if (detail.getSource() == FulfillmentDetailSource.ADDON_PURCHASE
                        && detail.getStartsAt() != null && detail.getStartsAt().isAfter(now)) {
                    detail.setStartsAt(now);
                    fulfillmentDetailRepository.save(detail);
                    log.info("Repaired addon FulfillmentDetail startsAt: detailId={}, featureCode={}, userId={}",
                            detail.getId(), detail.getFeatureCode(), userId);
                }
            }
        }
    }

    private String resolveAddonProductCode(Purchase purchase) {
        if (purchase.getProductId() != null) {
            return productRepository.findById(purchase.getProductId())
                    .map(Product::getCode)
                    .orElse(purchase.getPackageCode());
        }
        return purchase.getPackageCode();
    }

    private int resolveAddonQuantity(Purchase purchase) {
        if (purchase.getAddonQuantity() != null && purchase.getAddonQuantity() > 0) {
            return purchase.getAddonQuantity();
        }
        if (purchase.getInstallmentCount() != null && purchase.getInstallmentCount() > 0) {
            return purchase.getInstallmentCount();
        }
        return 1;
    }

    private String resolveFeatureCode(Product product) {
        if (product.getFeatureCode() != null && !product.getFeatureCode().isBlank()) {
            return product.getFeatureCode();
        }
        return product.getCode();
    }
}
