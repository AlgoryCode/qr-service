package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import com.ael.algoryqrservice.model.GrantFulfillment;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.ConsumedEntitlement;
import com.ael.algoryqrservice.model.dto.FulfillmentConsumeResult;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.FulfillmentReferenceType;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EntitlementService {

    private final UserEntitlementRepository entitlementRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final PlanPackageRepository planPackageRepository;
    private final PurchaseLogService purchaseLogService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final QrRepository qrRepository;
    private final ObjectProvider<PackageActivationService> packageActivationService;
    private final UserTrialService userTrialService;
    private final ObjectProvider<FulfillmentGateService> fulfillmentGateService;
    private final ObjectProvider<FulfillmentGrantService> fulfillmentGrantService;
    private final ObjectProvider<FulfillmentMigrationService> fulfillmentMigrationService;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;
    private final GrantFulfillmentRepository grantFulfillmentRepository;
    private final FulfillmentUsageLogRepository fulfillmentUsageLogRepository;
    private final Map<String, Consumer<Long>> syncRegistry;

    public EntitlementService(
            UserEntitlementRepository entitlementRepository,
            PurchaseRepository purchaseRepository,
            ProductRepository productRepository,
            PlanPackageRepository planPackageRepository,
            PurchaseLogService purchaseLogService,
            MenuPublicAccessService menuPublicAccessService,
            MenuRepository menuRepository,
            MenuProductRepository menuProductRepository,
            QrRepository qrRepository,
            ObjectProvider<PackageActivationService> packageActivationService,
            UserTrialService userTrialService,
            ObjectProvider<FulfillmentGateService> fulfillmentGateService,
            ObjectProvider<FulfillmentGrantService> fulfillmentGrantService,
            ObjectProvider<FulfillmentMigrationService> fulfillmentMigrationService,
            FulfillmentDetailRepository fulfillmentDetailRepository,
            GrantFulfillmentRepository grantFulfillmentRepository,
            FulfillmentUsageLogRepository fulfillmentUsageLogRepository
    ) {
        this.entitlementRepository = entitlementRepository;
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.planPackageRepository = planPackageRepository;
        this.purchaseLogService = purchaseLogService;
        this.menuPublicAccessService = menuPublicAccessService;
        this.menuRepository = menuRepository;
        this.menuProductRepository = menuProductRepository;
        this.qrRepository = qrRepository;
        this.packageActivationService = packageActivationService;
        this.userTrialService = userTrialService;
        this.fulfillmentGateService = fulfillmentGateService;
        this.fulfillmentGrantService = fulfillmentGrantService;
        this.fulfillmentMigrationService = fulfillmentMigrationService;
        this.fulfillmentDetailRepository = fulfillmentDetailRepository;
        this.grantFulfillmentRepository = grantFulfillmentRepository;
        this.fulfillmentUsageLogRepository = fulfillmentUsageLogRepository;
        this.syncRegistry = buildSyncRegistry();
    }

    private Map<String, Consumer<Long>> buildSyncRegistry() {
        Map<String, Consumer<Long>> registry = new HashMap<>();
        registry.put(CatalogProducts.QR_CREATE, this::syncQrCreateEntitlements);
        registry.put(CatalogProducts.QR_MENU, this::syncQrMenuUsageFromActiveMenus);
        registry.put(CatalogProducts.MENU_PRODUCT, this::syncMenuProductUsageFromActiveProducts);
        return registry;
    }

    @Transactional
    public void grant(Purchase purchase, Long productId, String productCode, int quantity, boolean unlimited) {
        if (entitlementRepository.findByPurchaseIdAndProductId(purchase.getId(), productId).isPresent()) {
            return;
        }
        UserEntitlement entitlement = UserEntitlement.builder()
                .userId(purchase.getUserId())
                .productId(productId)
                .productCode(productCode)
                .purchaseId(purchase.getId())
                .totalQuantity(quantity)
                .remainingQuantity(quantity)
                .usedQuantity(0)
                .unlimited(unlimited)
                .startsAt(purchase.getStartsAt())
                .expiresAt(purchase.getExpiresAt())
                .build();

        entitlementRepository.save(entitlement);
        purchaseLogService.log(
                purchase.getId(),
                purchase.getUserId(),
                PurchaseLogAction.ENTITLEMENT_GRANTED,
                (unlimited ? "Sınırsız " : quantity + " adet ") + productCode + " hakkı tanımlandı ("
                        + purchase.getStartsAt() + " - " + purchase.getExpiresAt() + ")"
        );
    }

    @Transactional
    public void synchronizePeriod(Purchase purchase) {
        List<UserEntitlement> entitlements = entitlementRepository
                .findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
        for (UserEntitlement entitlement : entitlements) {
            entitlement.setStartsAt(purchase.getStartsAt());
            entitlement.setExpiresAt(purchase.getExpiresAt());
        }
        entitlementRepository.saveAll(entitlements);
    }

    @Transactional
    public void ensureEntitlementsForPackage(Purchase purchase, PlanPackage planPackage) {
        Set<Long> keptProductIds = new HashSet<>();
        for (PlanPackageItem item : planPackage.getItems()) {
            Product product = item.getProduct();
            keptProductIds.add(product.getId());
            UserEntitlement entitlement = entitlementRepository
                    .findByPurchaseIdAndProductId(purchase.getId(), product.getId())
                    .orElse(null);
            if (entitlement == null) {
                grant(purchase, product.getId(), product.getCode(), item.getQuantity(), item.isUnlimited());
                continue;
            }
            entitlement.setProductCode(product.getCode());
            entitlement.setUnlimited(item.isUnlimited());
            entitlement.setStartsAt(purchase.getStartsAt());
            entitlement.setExpiresAt(purchase.getExpiresAt());
            if (item.isUnlimited()) {
                entitlement.setTotalQuantity(item.getQuantity());
            } else {
                int used = entitlement.getUsedQuantity() != null ? entitlement.getUsedQuantity() : 0;
                int total = item.getQuantity();
                if (used > total) {
                    used = total;
                    entitlement.setUsedQuantity(used);
                }
                entitlement.setTotalQuantity(total);
                entitlement.setRemainingQuantity(Math.max(0, total - used));
            }
            entitlementRepository.save(entitlement);
        }
        List<UserEntitlement> existing = entitlementRepository
                .findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
        for (UserEntitlement entitlement : existing) {
            if (!keptProductIds.contains(entitlement.getProductId())) {
                entitlement.setRemainingQuantity(0);
                entitlement.setExpiresAt(purchase.getExpiresAt());
                entitlementRepository.save(entitlement);
            }
        }
    }

    @Transactional
    public void refreshForPackage(Purchase purchase, PlanPackage planPackage) {
        Set<Long> keptProductIds = new HashSet<>();
        for (PlanPackageItem item : planPackage.getItems()) {
            Product product = item.getProduct();
            keptProductIds.add(product.getId());
            UserEntitlement entitlement = entitlementRepository
                    .findByPurchaseIdAndProductId(purchase.getId(), product.getId())
                    .orElse(null);
            if (entitlement == null) {
                grant(purchase, product.getId(), product.getCode(), item.getQuantity(), item.isUnlimited());
                continue;
            }
            entitlement.setProductCode(product.getCode());
            entitlement.setTotalQuantity(item.getQuantity());
            entitlement.setRemainingQuantity(item.isUnlimited() ? item.getQuantity() : item.getQuantity());
            entitlement.setUsedQuantity(0);
            entitlement.setUnlimited(item.isUnlimited());
            entitlement.setStartsAt(purchase.getStartsAt());
            entitlement.setExpiresAt(purchase.getExpiresAt());
            entitlementRepository.save(entitlement);
            purchaseLogService.log(
                    purchase.getId(),
                    purchase.getUserId(),
                    PurchaseLogAction.ENTITLEMENT_GRANTED,
                    (item.isUnlimited() ? "Sınırsız " : item.getQuantity() + " adet ")
                            + product.getCode() + " hakkı yenilendi ("
                            + purchase.getStartsAt() + " - " + purchase.getExpiresAt() + ")"
            );
        }
        List<UserEntitlement> existing = entitlementRepository
                .findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
        for (UserEntitlement entitlement : existing) {
            if (!keptProductIds.contains(entitlement.getProductId())) {
                entitlement.setRemainingQuantity(0);
                entitlement.setExpiresAt(purchase.getExpiresAt());
                entitlementRepository.save(entitlement);
            }
        }
    }

    @Transactional
    public void revokeForCancelledPurchase(Purchase purchase) {
        List<UserEntitlement> entitlements = entitlementRepository
                .findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
        for (UserEntitlement entitlement : entitlements) {
            entitlement.setExpiresAt(purchase.getExpiresAt());
            if (!entitlement.isUnlimited()) {
                entitlement.setRemainingQuantity(0);
            }
        }
        entitlementRepository.saveAll(entitlements);
    }

    @Transactional
    public ConsumedEntitlement consumeAddon(Long userId, String productCode, int amount) {
        return consumeInternal(userId, productCode, amount, true);
    }

    @Transactional
    public ConsumedEntitlement consume(Long userId, String productCode, int amount) {
        return consumeInternal(userId, productCode, amount, false);
    }

    private ConsumedEntitlement consumeInternal(Long userId, String productCode, int amount, boolean addonOnly) {
        expireDuePurchasesForUser(userId);
        ensureFulfillmentBackfill(userId);

        Product product = productRepository.findByCode(productCode).orElse(null);
        if (product != null && product.isRequiresCountSync()) {
            String featureKey = product.getFeatureCode() != null ? product.getFeatureCode() : product.getCode();
            Consumer<Long> syncFn = syncRegistry.get(featureKey);
            if (syncFn != null) {
                syncFn.accept(userId);
            }
        }

        if (product != null && !product.isConsumable()) {
            requireScope(userId, product.getScopeCode());
            return null;
        }

        String featureCode = resolveFeatureCode(product, productCode);
        FulfillmentGateService gate = fulfillmentGateService.getObject();
        FulfillmentConsumeResult result = addonOnly
                ? gate.consumeAddon(userId, featureCode, amount, FulfillmentReferenceType.FEATURE, null)
                : gate.consumeFeature(userId, featureCode, amount, FulfillmentReferenceType.FEATURE, null);

        if (!result.fullyConsumed(amount)) {
            if (product != null && matchesFeature(product, CatalogProducts.QR_MENU)) {
                throw new ForbiddenException(
                        addonOnly ? "EXTRA_MENU_REQUIRED" : null,
                        "Yetersiz dijital menü hakkı. Lütfen paket satın alın veya mevcut bir menüyü pasif yaparak slot açın."
                );
            }
            if (product != null && matchesFeature(product, CatalogProducts.MENU_PRODUCT)) {
                throw new ForbiddenException(
                        "Yetersiz menü ürün hakkı. Lütfen paket satın alın veya paketinizi yükseltin."
                );
            }
            String displayCode = product != null ? product.getCode() : productCode;
            throw new ForbiddenException("Yetersiz veya süresi dolmuş " + displayCode + " hakkı. Lütfen paket satın alın.");
        }

        return new ConsumedEntitlement(result.purchaseId(), result.detailId(), amount);
    }

    @Transactional
    public void release(Long userId, String productCode, int amount) {
        if (userId == null || amount <= 0) {
            return;
        }
        expireDuePurchasesForUser(userId);
        ensureFulfillmentBackfill(userId);
        Product product = productRepository.findByCode(productCode).orElse(null);
        if (product != null && !product.isConsumable()) {
            return;
        }
        String featureCode = resolveFeatureCode(product, productCode);
        fulfillmentGateService.getObject().releaseFeature(
                userId, featureCode, amount, FulfillmentReferenceType.FEATURE, null
        );
    }

    @Transactional(readOnly = true)
    public boolean hasScope(Long userId, String scopeCode) {
        return fulfillmentGateService.getObject().hasScope(userId, scopeCode);
    }

    @Transactional
    public void requireScope(Long userId, String scopeCode) {
        expireDuePurchasesForUser(userId);
        repairUsablePackageEntitlements(userId);
        ensureFulfillmentBackfill(userId);
        if (!hasScope(userId, scopeCode)) {
            throw new ForbiddenException(scopeCode + " yetkisi için uygun paket gerekli");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasUsableQrCreatePackage(Long userId) {
        return fulfillmentGateService.getObject().hasScope(userId, CatalogScopes.QR_CREATE_OWNER);
    }

    @Transactional
    public List<UserEntitlementResponse> getUserEntitlements(Long userId) {
        expireDuePurchasesForUser(userId);
        repairUsablePackageEntitlements(userId);
        ensureFulfillmentBackfill(userId);
        return toResponsesFromFulfillment(userId);
    }

    @Transactional
    public void repairUsablePackageEntitlements(Long userId) {
        List<Purchase> usablePurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.FREE)
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.ADD_ON)
                .filter(purchase -> !purchase.isSystemManaged())
                .toList();
        for (Purchase purchase : usablePurchases) {
            if (purchase.getPackageId() == null) {
                continue;
            }
            planPackageRepository.findByIdWithItems(purchase.getPackageId())
                    .ifPresent(planPackage -> ensureEntitlementsForPackage(purchase, planPackage));
        }
        repairAddonEntitlements(userId);
        ensureFulfillmentBackfill(userId);
        syncQrMenuUsageFromActiveMenus(userId);
        syncQrCreateEntitlements(userId);
        syncMenuProductUsageFromActiveProducts(userId);
    }

    @Transactional
    public void repairAddonEntitlements(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Purchase> addonPurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .filter(purchase -> purchase.getPurchaseType() == PurchaseType.ADD_ON)
                .toList();
        fulfillmentGrantService.getObject().repairAddonFulfillmentsForUser(userId);
        for (Purchase purchase : addonPurchases) {
            if (purchase.getStartsAt() != null && purchase.getStartsAt().isAfter(now)) {
                purchase.setStartsAt(now);
                purchaseRepository.save(purchase);
            }
            Product product = productRepository.findByCode(purchase.getPackageCode()).orElse(null);
            if (product == null) {
                continue;
            }
            int quantity = purchase.getInstallmentCount() == null || purchase.getInstallmentCount() < 1
                    ? 1
                    : purchase.getInstallmentCount();
            List<UserEntitlement> entitlements = entitlementRepository
                    .findByPurchaseIdOrderByProductCodeAsc(purchase.getId());
            UserEntitlement matched = null;
            for (UserEntitlement entitlement : entitlements) {
                if (Objects.equals(entitlement.getProductId(), product.getId())
                        || Objects.equals(entitlement.getProductCode(), product.getCode())) {
                    matched = entitlement;
                    continue;
                }
                entitlement.setRemainingQuantity(0);
                entitlement.setExpiresAt(purchase.getExpiresAt() != null
                        ? purchase.getExpiresAt()
                        : LocalDateTime.now());
                entitlementRepository.save(entitlement);
            }
            if (matched == null) {
                grant(purchase, product.getId(), product.getCode(), quantity, false);
                continue;
            }
            matched.setProductId(product.getId());
            matched.setProductCode(product.getCode());
            matched.setUnlimited(false);
            matched.setStartsAt(purchase.getStartsAt());
            matched.setExpiresAt(purchase.getExpiresAt());
            int used = matched.getUsedQuantity() != null ? matched.getUsedQuantity() : 0;
            if (used > quantity) {
                used = quantity;
                matched.setUsedQuantity(used);
            }
            matched.setTotalQuantity(quantity);
            matched.setRemainingQuantity(Math.max(0, quantity - used));
            entitlementRepository.save(matched);
        }
    }

    @Transactional(readOnly = true)
    public Long resolveActivePurchaseId(Long userId) {
        if (userId == null) {
            return null;
        }
        expireDuePurchasesForUser(userId);
        List<Purchase> activePurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .toList();
        if (activePurchases.isEmpty()) {
            return null;
        }
        List<Purchase> paidOrTrial = activePurchases.stream()
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.FREE)
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.ADD_ON)
                .filter(purchase -> !purchase.isSystemManaged())
                .toList();
        if (!paidOrTrial.isEmpty()) {
            return selectHighestPriorityPurchase(paidOrTrial).map(Purchase::getId).orElse(null);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public boolean isActivePurchase(Long userId, Long purchaseId) {
        if (userId == null || purchaseId == null) {
            return false;
        }
        return Objects.equals(resolveActivePurchaseId(userId), purchaseId);
    }

    private java.util.Optional<Purchase> selectHighestPriorityPurchase(List<Purchase> purchases) {
        Map<Long, PlanPackage> packagesById = planPackageRepository.findAllById(
                purchases.stream().map(Purchase::getPackageId).distinct().toList()
        ).stream().collect(Collectors.toMap(PlanPackage::getId, Function.identity(), (left, right) -> left));

        return purchases.stream()
                .max(java.util.Comparator.comparingInt(purchase -> {
                    PlanPackage planPackage = packagesById.get(purchase.getPackageId());
                    if (planPackage != null && planPackage.getPriority() != null) {
                        return planPackage.getPriority();
                    }
                    return 0;
                }));
    }

    @Transactional
    public void syncQrCreateEntitlements(Long userId) {
        if (userId == null) {
            return;
        }

        long activeQrCount = qrRepository.countByUserIdAndDeletedFalse(userId);
        fulfillmentGateService.getObject().replaceUsedQuantity(
                userId, CatalogProducts.QR_CREATE, (int) Math.min(activeQrCount, Integer.MAX_VALUE), false
        );
    }

    @Transactional
    public void syncMenuEntitlements(Long userId) {
        syncQrMenuUsageFromActiveMenus(userId);
    }

    @Transactional
    public void assertMenuActivationAllowed(Long userId) {
        repairUsablePackageEntitlements(userId);
        if (sumRemainingMenuSlots(userId) <= 0) {
            throw new ForbiddenException(
                    "Yetersiz dijital menü hakkı. Başka bir menüyü pasif yapın veya paket yükseltin."
            );
        }
    }

    private int sumRemainingMenuSlots(Long userId) {
        return fulfillmentGateService.getObject().remainingQuantity(userId, CatalogProducts.QR_MENU, true);
    }

    private void syncQrMenuUsageFromActiveMenus(Long userId) {
        if (userId == null) {
            return;
        }

        int extraMenus = 0;
        for (Object[] row : menuRepository.countActiveLiveMenusGroupedByBranch(userId)) {
            extraMenus += (int) Math.max(0, ((Number) row[1]).longValue() - 1);
        }
        fulfillmentGateService.getObject().replaceUsedQuantity(userId, CatalogProducts.QR_MENU, extraMenus, true);
    }

    @Transactional(readOnly = true)
    public List<UserEntitlementResponse> getPurchaseEntitlements(Purchase purchase) {
        GrantFulfillment grant = grantFulfillmentRepository.findByPurchaseId(purchase.getId()).orElse(null);
        if (grant != null) {
            Map<Long, LocalDateTime> lastUsageByDetail = lastUsageByDetail(purchase.getUserId());
            return fulfillmentDetailRepository.findByFulfillmentId(grant.getId()).stream()
                    .map(detail -> toResponse(detail, purchase, lastUsageByDetail.get(detail.getId())))
                    .toList();
        }
        return entitlementRepository.findByPurchaseIdOrderByProductCodeAsc(purchase.getId()).stream()
                .map(entitlement -> toResponse(entitlement, purchase))
                .toList();
    }

    @Transactional
    public void expireDuePurchases() {
        List<Purchase> duePurchases = purchaseRepository.findByStatusAndExpiresAtBefore(
                PurchaseStatus.ACTIVE,
                LocalDateTime.now()
        );

        Set<Long> userIds = new HashSet<>();
        for (Purchase purchase : duePurchases) {
            expirePurchaseInternal(purchase);
            userIds.add(purchase.getUserId());
        }
        restoreSubscriptionAndSync(userIds);
    }

    @Transactional
    public void expireDuePurchasesForUser(Long userId) {
        if (userId == null) {
            return;
        }
        List<Purchase> duePurchases = purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(
                userId,
                PurchaseStatus.ACTIVE,
                LocalDateTime.now()
        );
        duePurchases.forEach(this::expirePurchaseInternal);
        if (!duePurchases.isEmpty()) {
            restoreSubscriptionAndSync(Set.of(userId));
        }
    }

    @Transactional
    public void expirePurchase(Purchase purchase) {
        expirePurchaseInternal(purchase);
        restoreSubscriptionAndSync(Set.of(purchase.getUserId()));
    }

    private void expirePurchaseInternal(Purchase purchase) {
        if (purchase.getStatus() != PurchaseStatus.ACTIVE) {
            return;
        }

        purchase.setStatus(PurchaseStatus.EXPIRED);
        purchaseRepository.save(purchase);

        if (purchase.getPurchaseType() == PurchaseType.TRIAL) {
            userTrialService.markTrialCompleted(purchase.getUserId(), purchase.getExpiresAt());
        }

        purchaseLogService.log(
                purchase.getId(),
                purchase.getUserId(),
                PurchaseLogAction.PURCHASE_EXPIRED,
                purchase.getPackageName() + " paketi süresi doldu (" + purchase.getExpiresAt() + ")"
        );

    }

    @Transactional
    public void assertMenuProductCreationAllowed(Long userId, int additionalProducts) {
        if (userId == null) {
            throw new ForbiddenException("Menü ürün hakkı için oturum gerekli");
        }
        if (additionalProducts < 1) {
            return;
        }
        repairUsablePackageEntitlements(userId);
        syncMenuProductUsageFromActiveProducts(userId);
        if (sumRemainingMenuProductSlots(userId) < additionalProducts) {
            throw new ForbiddenException(
                    "Yetersiz menü ürün hakkı. Paket limitinize ulaştınız veya paket satın almanız gerekiyor."
            );
        }
    }

    @Transactional
    public void syncMenuProductEntitlements(Long userId) {
        syncMenuProductUsageFromActiveProducts(userId);
    }

    private int sumRemainingMenuProductSlots(Long userId) {
        return fulfillmentGateService.getObject().remainingQuantity(userId, CatalogProducts.MENU_PRODUCT, false);
    }

    private void syncMenuProductUsageFromActiveProducts(Long userId) {
        if (userId == null) {
            return;
        }
        long activeProducts = menuProductRepository.countActiveProductsForUser(userId);
        fulfillmentGateService.getObject().replaceUsedQuantity(
                userId, CatalogProducts.MENU_PRODUCT, (int) Math.min(activeProducts, Integer.MAX_VALUE), false
        );
    }

    private void restoreSubscriptionAndSync(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        PackageActivationService activationService = packageActivationService.getObject();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            activationService.ensureSubscriptionState(userId);
            menuPublicAccessService.syncForUser(userId);
        }
    }

    private Set<String> productCodesByFeature(String featureCode) {
        return productRepository.findByFeatureCode(featureCode).stream()
                .map(Product::getCode)
                .collect(Collectors.toSet());
    }

    private boolean matchesFeature(Product product, String featureCode) {
        if (product == null || featureCode == null) {
            return false;
        }
        return featureCode.equals(product.getFeatureCode()) || featureCode.equals(product.getCode());
    }

    private Map<Long, Purchase> loadPurchases(List<UserEntitlement> entitlements) {
        List<Long> purchaseIds = entitlements.stream()
                .map(UserEntitlement::getPurchaseId)
                .distinct()
                .toList();

        return purchaseRepository.findAllById(purchaseIds).stream()
                .collect(Collectors.toMap(Purchase::getId, Function.identity()));
    }

    private void ensureFulfillmentBackfill(Long userId) {
        if (userId == null) {
            return;
        }
        fulfillmentMigrationService.getObject().backfillUser(userId);
    }

    private String resolveFeatureCode(Product product, String productCode) {
        if (product != null && product.getFeatureCode() != null && !product.getFeatureCode().isBlank()) {
            return product.getFeatureCode();
        }
        if (product != null) {
            return product.getCode();
        }
        return productCode;
    }

    private List<UserEntitlementResponse> toResponsesFromFulfillment(Long userId) {
        Map<Long, LocalDateTime> lastUsageByDetail = lastUsageByDetail(userId);
        List<FulfillmentDetail> details = fulfillmentDetailRepository.findAllActiveByUserId(userId, AppTime.nowLocal());
        List<Long> grantIds = details.stream().map(FulfillmentDetail::getFulfillmentId).distinct().toList();
        Map<Long, GrantFulfillment> grants = grantFulfillmentRepository.findAllById(grantIds).stream()
                .collect(Collectors.toMap(GrantFulfillment::getId, Function.identity(), (left, right) -> left));
        Map<Long, Purchase> purchases = purchaseRepository.findAllById(
                grants.values().stream().map(GrantFulfillment::getPurchaseId).distinct().toList()
        ).stream().collect(Collectors.toMap(Purchase::getId, Function.identity(), (left, right) -> left));
        return details.stream()
                .map(detail -> {
                    GrantFulfillment grant = grants.get(detail.getFulfillmentId());
                    Purchase purchase = grant == null ? null : purchases.get(grant.getPurchaseId());
                    return toResponse(detail, purchase, lastUsageByDetail.get(detail.getId()));
                })
                .toList();
    }

    private Map<Long, LocalDateTime> lastUsageByDetail(Long userId) {
        Map<Long, LocalDateTime> lastUsage = new HashMap<>();
        List<FulfillmentUsageLog> logs = fulfillmentUsageLogRepository.findByUserIdOrderByCreatedAtDesc(
                userId, org.springframework.data.domain.PageRequest.of(0, 200)
        ).getContent();
        for (FulfillmentUsageLog log : logs) {
            lastUsage.putIfAbsent(log.getDetailId(), log.getCreatedAt());
        }
        return lastUsage;
    }

    private UserEntitlementResponse toResponse(FulfillmentDetail detail, Purchase purchase, LocalDateTime lastUsage) {
        String productName = detail.getProductId() == null
                ? detail.getFeatureCode()
                : productRepository.findById(detail.getProductId()).map(Product::getName).orElse(detail.getFeatureCode());
        boolean usable = purchase != null && purchase.isUsable();
        boolean expired = purchase == null || purchase.isEffectivelyExpired()
                || (detail.getExpiresAt() != null && detail.getExpiresAt().isBefore(AppTime.nowLocal()));
        return UserEntitlementResponse.builder()
                .id(detail.getId())
                .productId(detail.getProductId())
                .productCode(detail.getFeatureCode())
                .productName(productName)
                .purchaseId(purchase == null ? null : purchase.getId())
                .totalQuantity(detail.isUnlimited() ? 0 : detail.getQuantity())
                .remainingQuantity(detail.isUnlimited() ? Integer.MAX_VALUE : detail.remainingQuantity())
                .usedQuantity(detail.getUsedQuantity())
                .unlimited(detail.isUnlimited())
                .startsAt(detail.getStartsAt())
                .expiresAt(detail.getExpiresAt())
                .lastUsage(lastUsage)
                .purchaseStatus(purchase == null ? PurchaseStatus.EXPIRED : purchase.getStatus())
                .expired(expired)
                .usable(usable)
                .createdAt(detail.getCreatedAt())
                .build();
    }

    UserEntitlementResponse toResponse(UserEntitlement entitlement, Purchase purchase) {
        String productName = productRepository.findById(entitlement.getProductId())
                .map(Product::getName)
                .orElse(entitlement.getProductCode());

        PurchaseStatus purchaseStatus = purchase != null ? purchase.getStatus() : PurchaseStatus.EXPIRED;
        boolean expired = purchase == null
                || purchase.isEffectivelyExpired()
                || entitlement.isExpiredByDate();
        boolean usable = purchase != null && entitlement.isUsable(purchase);

        return UserEntitlementResponse.builder()
                .id(entitlement.getId())
                .productId(entitlement.getProductId())
                .productCode(entitlement.getProductCode())
                .productName(productName)
                .purchaseId(entitlement.getPurchaseId())
                .totalQuantity(entitlement.getTotalQuantity())
                .remainingQuantity(entitlement.getRemainingQuantity())
                .usedQuantity(entitlement.getUsedQuantity())
                .unlimited(entitlement.isUnlimited())
                .startsAt(entitlement.getStartsAt())
                .expiresAt(entitlement.getExpiresAt())
                .lastUsage(entitlement.getLastUsage())
                .purchaseStatus(purchaseStatus)
                .expired(expired)
                .usable(usable)
                .createdAt(entitlement.getCreatedAt())
                .build();
    }
}
