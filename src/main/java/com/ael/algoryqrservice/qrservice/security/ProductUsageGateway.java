package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.fulfillmentservice.application.metering.MeteringService;
import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalConsumeResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalProductAccessResponse;
import com.ael.algoryqrservice.qrservice.billing.service.UpgradeOptionService;
import com.ael.algoryqrservice.qrservice.exception.EntitlementDeniedException;
import com.ael.algoryqrservice.qrservice.exception.EntitlementDeniedException.Upgrade;
import com.ael.algoryqrservice.qrservice.model.Feature;
import com.ael.algoryqrservice.qrservice.repository.FeatureRepository;
import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.exception.FulfillmentQuotaExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ProductUsageGateway {

    private static final String ACTIVE = "ACTIVE";
    private static final String ACCESS_KEY = ProductUsageGateway.class.getName() + ".access:";
    private static final String ENTITLEMENTS_KEY = ProductUsageGateway.class.getName() + ".entitlements:";

    private final FulfillmentServiceClient fulfillmentClient;
    private final UpgradeOptionService upgradeOptions;
    private final ProductRepository productRepository;
    private final FeatureRepository featureRepository;

    public void allow(Long merchantId, String productCode) {
        ExternalProductAccessResponse access = readAccess(merchantId, productCode);
        if (!access.allowed()) {
            deny(access, productCode);
        }
    }

    public ExternalConsumeResponse use(Long merchantId, String productCode, int quantity) {
        ExternalProductAccessResponse access = readAccess(merchantId, productCode);
        if (!access.allowed()) {
            deny(access, productCode);
        }
        if (quantity <= 0 || !countable(fulfillmentClient, merchantId, productCode)) {
            return null;
        }
        try {
            return fulfillmentClient.consume(merchantId, productCode, quantity);
        } catch (FulfillmentQuotaExceededException exception) {
            throw quotaExceeded(productCode, access.packageCode(), exception.limit(), exception.used());
        } finally {
            forget(ACCESS_KEY + merchantId + ":" + productCode);
            forget(ENTITLEMENTS_KEY + merchantId);
        }
    }

    /**
     * Gives back usage when the counted thing (a branch, a menu product) is deleted, so the merchant
     * can create another within the same limit. Best effort: a failure here must not block the delete.
     */
    public void free(Long merchantId, String productCode, int quantity) {
        if (merchantId == null || quantity <= 0) {
            return;
        }
        try {
            fulfillmentClient.release(merchantId, productCode, quantity);
            forget(ACCESS_KEY + merchantId + ":" + productCode);
            forget(ENTITLEMENTS_KEY + merchantId);
        } catch (RuntimeException exception) {
            log.warn("Usage release failed. merchantId={} product={} quantity={}", merchantId, productCode, quantity, exception);
        }
    }

    private ExternalProductAccessResponse readAccess(Long merchantId, String productCode) {
        return perRequest(ACCESS_KEY + merchantId + ":" + productCode,
                () -> fulfillmentClient.findProductAccess(merchantId, productCode));
    }

    private boolean countable(FulfillmentServiceClient client, Long merchantId, String productCode) {
        List<ExternalEntitlementResponse> entitlements =
                perRequest(ENTITLEMENTS_KEY + merchantId, () -> client.listEntitlements(merchantId));
        return entitlements.stream()
                .filter(item -> matches(item, productCode))
                .filter(this::open)
                .anyMatch(item -> !item.unlimited());
    }

    private boolean matches(ExternalEntitlementResponse item, String productCode) {
        return productCode.equals(item.productCode()) || productCode.equals(item.featureCode());
    }

    private boolean open(ExternalEntitlementResponse item) {
        if (item.status() != null && !ACTIVE.equals(item.status())) {
            return false;
        }
        return item.expiresAt() == null || item.expiresAt().isAfter(Instant.now());
    }

    private void deny(ExternalProductAccessResponse access, String productCode) {
        String feature = featureOf(productCode);
        if (access.packageCode() == null || access.packageCode().isBlank()) {
            throw new EntitlementDeniedException(
                    EntitlementDeniedException.NO_ACTIVE_PLAN,
                    "Bu özellik için aktif bir paket gerekli.",
                    feature, null, null, null,
                    upgradeOptions.forFeature(feature, null, null));
        }
        throw new ProductNotInPackageException(
                access.packageCode(), feature, upgradeOptions.forFeature(feature, null, access.packageCode()));
    }

    /**
     * Spends a per-period allowance ({@code features.kind = METERED}) or refuses with
     * {@code LIMIT_REACHED}, the numbers and what would give more. A repeated key is charged once.
     */
    public MeteringService.Charge meter(Long merchantId, String featureKey, int quantity, String idempotencyKey,
                                        String reference) {
        MeteringService.Charge charge = fulfillmentClient.meter(merchantId, featureKey, quantity, idempotencyKey, reference);
        if (!charge.allowed()) {
            String packageCode = fulfillmentClient.findActivePackage(merchantId)
                    .map(ExternalActivePackageResponse::packageCode)
                    .orElse(null);
            EntitlementDeniedException denied = quotaExceeded(featureKey, packageCode, charge.usage().limit(), charge.usage().used());
            if (denied.upgrades().isEmpty()) {
                // A free allowance (e.g. assistant questions) is lifted by buying the feature it belongs to.
                List<Upgrade> viaParent = featureRepository.findById(featureKey)
                        .map(Feature::getParentKey)
                        .map(parent -> upgradeOptions.forFeature(parent, null, packageCode))
                        .orElse(List.of());
                denied = new EntitlementDeniedException(denied.getCode(), denied.getMessage(), featureKey, packageCode,
                        charge.usage().limit(), charge.usage().used(), viaParent);
            }
            throw denied;
        }
        return charge;
    }

    /** Gives a {@link #meter} spend back. */
    public boolean unmeter(String idempotencyKey) {
        return fulfillmentClient.releaseMeter(idempotencyKey);
    }

    private EntitlementDeniedException quotaExceeded(String productCode, String packageCode, Long limit, Long used) {
        String feature = featureOf(productCode);
        String message = switch (feature) {
            case CatalogProducts.QR_BRANCH -> "Şube limitinize ulaştınız. Ek şube alın ya da paketinizi yükseltin.";
            case CatalogProducts.QR_MENU -> "Menü limitinize ulaştınız. Ek menü alın ya da paketinizi yükseltin.";
            case CatalogProducts.MENU_PRODUCT -> "Ürün limitinize ulaştınız. Paketinizi yükseltin.";
            case CatalogProducts.QR_CREATE -> "QR kod limitinize ulaştınız. Paketinizi yükseltin.";
            case CatalogProducts.ASSISTANT_QUESTION ->
                    "Bugünkü ücretsiz asistan sorularınızı kullandınız. Sınırsız asistan için paketinizi yükseltin.";
            case CatalogProducts.AI_MENU_IMPORT_RUN -> "Bu ayki AI menü aktarımı hakkınız doldu. Paketinizi yükseltin.";
            default -> "Bu özellik için hakkınız kalmadı.";
        };
        return new EntitlementDeniedException(
                EntitlementDeniedException.LIMIT_REACHED, message, feature, packageCode, limit, used,
                upgradeOptions.forFeature(feature, limit, packageCode));
    }

    private String featureOf(String productCode) {
        return productRepository.findByCode(productCode)
                .map(product -> product.getFeatureCode() == null || product.getFeatureCode().isBlank()
                        ? product.getCode()
                        : product.getFeatureCode())
                .orElse(productCode);
    }

    static final class ProductNotInPackageException extends EntitlementDeniedException {

        private ProductNotInPackageException(String packageCode, String feature, List<Upgrade> upgrades) {
            super(FEATURE_NOT_IN_PLAN, "Bu özellik paketinizde yok.", feature, packageCode, null, null, upgrades);
        }
    }

    /**
     * One HTTP request often checks the same product several times (class and method annotations,
     * then a quota use). The answer is kept for the rest of that request only, so a purchase or a
     * cancellation is seen by the very next request. Outside a web request nothing is cached.
     */
    @SuppressWarnings("unchecked")
    private static <T> T perRequest(String key, Supplier<T> loader) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return loader.get();
        }
        Object cached = attributes.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
        if (cached != null) {
            return (T) cached;
        }
        T value = loader.get();
        if (value != null) {
            attributes.setAttribute(key, value, RequestAttributes.SCOPE_REQUEST);
        }
        return value;
    }

    private static void forget(String key) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.removeAttribute(key, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
