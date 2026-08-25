package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.ConsumedEntitlement;
import com.ael.algoryqrservice.model.dto.FulfillmentConsumeResult;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.service.entitlement.EntitlementMaintenanceService;
import com.ael.algoryqrservice.service.entitlement.FeatureUsageSyncRegistry;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.service.entitlement.UserEntitlementQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application-facing entitlement gate: answers "may this user do X" and books the usage.
 *
 * <p>Storage details live in {@link com.ael.algoryqrservice.service.fulfillment}, repair and
 * expiry in {@link com.ael.algoryqrservice.service.entitlement}. This class only translates a
 * catalog product code into a feature code and turns an exhausted quota into a domain error.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private static final String MISSING_SCOPE_MESSAGE_SUFFIX = " yetkisi için uygun paket gerekli";
    private static final String ANONYMOUS_MENU_PRODUCT_MESSAGE = "Menü ürün hakkı için oturum gerekli";
    private static final String MENU_PRODUCT_LIMIT_MESSAGE =
            "Yetersiz menü ürün hakkı. Paket limitinize ulaştınız veya paket satın almanız gerekiyor.";

    private static final Map<String, String> EXHAUSTED_QUOTA_MESSAGES = Map.of(
            CatalogProducts.QR_MENU,
            "Yetersiz dijital menü hakkı. Lütfen paket satın alın veya mevcut bir menüyü pasif yaparak slot açın.",
            CatalogProducts.MENU_PRODUCT,
            "Yetersiz menü ürün hakkı. Lütfen paket satın alın veya paketinizi yükseltin."
    );

    private final ProductRepository productRepository;
    private final FulfillmentGateService fulfillmentGateService;
    private final PurchaseExpiryService purchaseExpiryService;
    private final EntitlementMaintenanceService maintenanceService;
    private final FeatureUsageSyncRegistry usageSyncRegistry;
    private final UserEntitlementQueryService entitlementQueryService;

    @Transactional(readOnly = true)
    public boolean hasScope(Long userId, String scopeCode) {
        return fulfillmentGateService.hasScope(userId, scopeCode);
    }

    @Transactional
    public void requireScope(Long userId, String scopeCode) {
        refreshUserState(userId);
        if (!fulfillmentGateService.hasScope(userId, scopeCode)) {
            throw new ForbiddenException(scopeCode + MISSING_SCOPE_MESSAGE_SUFFIX);
        }
    }

    @Transactional
    public List<UserEntitlementResponse> getUserEntitlements(Long userId) {
        refreshUserState(userId);
        return entitlementQueryService.forUser(userId);
    }

    /**
     * Books {@code amount} units of a consumable product.
     *
     * @return the quota row the usage was booked against, or {@code null} for scope-only products.
     * @throws ForbiddenException when the user does not hold enough quota.
     */
    @Transactional
    public ConsumedEntitlement consume(Long userId, String productCode, int amount) {
        purchaseExpiryService.expireDueForUser(userId);
        maintenanceService.backfillFulfillment(userId);

        Optional<Product> product = findProduct(productCode);
        String featureCode = resolveFeatureCode(product, productCode);
        if (product.filter(Product::isRequiresCountSync).isPresent()) {
            usageSyncRegistry.synchronize(userId, featureCode);
        }
        if (product.filter(candidate -> !candidate.isConsumable()).isPresent()) {
            requireScope(userId, product.get().getScopeCode());
            return null;
        }

        FulfillmentConsumeResult result = fulfillmentGateService.consumeFeature(
                userId, featureCode, amount, FulfillmentReferenceType.FEATURE, null
        );
        if (!result.fullyConsumed(amount)) {
            throw new ForbiddenException(exhaustedQuotaMessage(featureCode));
        }
        return new ConsumedEntitlement(result.purchaseId(), result.detailId(), amount);
    }

    @Transactional
    public void release(Long userId, String productCode, int amount) {
        if (userId == null || amount <= 0) {
            return;
        }
        purchaseExpiryService.expireDueForUser(userId);
        maintenanceService.backfillFulfillment(userId);

        Optional<Product> product = findProduct(productCode);
        if (product.filter(candidate -> !candidate.isConsumable()).isPresent()) {
            return;
        }
        fulfillmentGateService.releaseFeature(
                userId, resolveFeatureCode(product, productCode), amount, FulfillmentReferenceType.FEATURE, null
        );
    }

    @Transactional
    public void assertMenuProductCreationAllowed(Long userId, int additionalProducts) {
        if (userId == null) {
            throw new ForbiddenException(ANONYMOUS_MENU_PRODUCT_MESSAGE);
        }
        if (additionalProducts < 1) {
            return;
        }
        maintenanceService.repairUser(userId);
        int remaining = fulfillmentGateService.remainingQuantity(userId, CatalogProducts.MENU_PRODUCT, false);
        if (remaining < additionalProducts) {
            throw new ForbiddenException(MENU_PRODUCT_LIMIT_MESSAGE);
        }
    }

    private void refreshUserState(Long userId) {
        purchaseExpiryService.expireDueForUser(userId);
        maintenanceService.repairUser(userId);
    }

    private Optional<Product> findProduct(String productCode) {
        return productCode == null ? Optional.empty() : productRepository.findByCode(productCode);
    }

    private String resolveFeatureCode(Optional<Product> product, String productCode) {
        return product
                .map(candidate -> isBlank(candidate.getFeatureCode()) ? candidate.getCode() : candidate.getFeatureCode())
                .orElse(productCode);
    }

    private String exhaustedQuotaMessage(String featureCode) {
        return EXHAUSTED_QUOTA_MESSAGES.getOrDefault(
                featureCode,
                "Yetersiz veya süresi dolmuş " + featureCode + " hakkı. Lütfen paket satın alın."
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
