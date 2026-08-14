package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.UserEntitlement;
import com.ael.algoryqrservice.model.dto.ConsumedEntitlement;
import com.ael.algoryqrservice.model.dto.UserEntitlementResponse;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
            ObjectProvider<PackageActivationService> packageActivationService
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
    public ConsumedEntitlement consume(Long userId, String productCode, int amount) {
        expireDuePurchasesForUser(userId);

        if (Objects.equals(productCode, CatalogProducts.QR_CREATE)) {
            syncQrCreateUsageFromActiveQrs(userId);
        }

        Product product = productRepository.findByCode(productCode).orElse(null);
        if (product != null && !product.isConsumable()) {
            requireScope(userId, product.getScopeCode());
            return null;
        }

        List<UserEntitlement> entitlements = entitlementRepository
                .findUsableByUserIdOrderByCreatedAtAsc(userId, 0);

        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);

        int remainingToConsume = amount;
        Long purchaseIdForLog = null;
        Long entitlementIdForLog = null;

        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), productCode)) {
                continue;
            }

            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            if (purchase == null || !entitlement.isUsable(purchase)) {
                continue;
            }

            if (remainingToConsume <= 0) {
                break;
            }

            if (entitlement.isUnlimited()) {
                remainingToConsume = 0;
                purchaseIdForLog = entitlement.getPurchaseId();
                entitlementIdForLog = entitlement.getId();
                break;
            }

            int consumed = Math.min(entitlement.getRemainingQuantity(), remainingToConsume);
            entitlement.setRemainingQuantity(entitlement.getRemainingQuantity() - consumed);
            entitlement.setUsedQuantity(entitlement.getUsedQuantity() + consumed);
            entitlementRepository.save(entitlement);

            remainingToConsume -= consumed;
            purchaseIdForLog = entitlement.getPurchaseId();
            entitlementIdForLog = entitlement.getId();
        }

        if (remainingToConsume > 0) {
            if (Objects.equals(productCode, CatalogProducts.QR_MENU)) {
                throw new ForbiddenException(
                        "Yetersiz dijital menü hakkı. Lütfen paket satın alın veya mevcut bir menüyü pasif yaparak slot açın."
                );
            }
            if (Objects.equals(productCode, CatalogProducts.MENU_PRODUCT)) {
                throw new ForbiddenException(
                        "Yetersiz menü ürün hakkı. Lütfen paket satın alın veya paketinizi yükseltin."
                );
            }
            throw new ForbiddenException("Yetersiz veya süresi dolmuş " + productCode + " hakkı. Lütfen paket satın alın.");
        }

        if (purchaseIdForLog != null) {
            purchaseLogService.log(
                    purchaseIdForLog,
                    userId,
                    PurchaseLogAction.ENTITLEMENT_CONSUMED,
                    amount + " adet " + productCode + " hakkı kullanıldı"
            );
        }

        if (purchaseIdForLog == null) {
            return null;
        }
        return new ConsumedEntitlement(purchaseIdForLog, entitlementIdForLog, amount);
    }

    @Transactional
    public void release(Long userId, String productCode, int amount) {
        if (userId == null || amount <= 0) {
            return;
        }

        expireDuePurchasesForUser(userId);

        Product product = productRepository.findByCode(productCode).orElse(null);
        if (product != null && !product.isConsumable()) {
            return;
        }

        List<UserEntitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);

        int remainingToRelease = amount;
        Long purchaseIdForLog = null;

        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), productCode)) {
                continue;
            }

            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            if (purchase == null || !entitlement.isUsable(purchase)) {
                continue;
            }

            if (remainingToRelease <= 0) {
                break;
            }

            if (entitlement.isUnlimited()) {
                remainingToRelease = 0;
                break;
            }

            int used = entitlement.getUsedQuantity() != null ? entitlement.getUsedQuantity() : 0;
            if (used <= 0) {
                continue;
            }

            int released = Math.min(used, remainingToRelease);
            int total = entitlement.getTotalQuantity() != null ? entitlement.getTotalQuantity() : 0;
            entitlement.setUsedQuantity(used - released);
            entitlement.setRemainingQuantity(Math.max(0, total - entitlement.getUsedQuantity()));
            entitlementRepository.save(entitlement);

            remainingToRelease -= released;
            purchaseIdForLog = entitlement.getPurchaseId();
        }

        if (purchaseIdForLog != null && remainingToRelease < amount) {
            purchaseLogService.log(
                    purchaseIdForLog,
                    userId,
                    PurchaseLogAction.ENTITLEMENT_CONSUMED,
                    (amount - remainingToRelease) + " adet " + productCode + " hakkı geri verildi"
            );
        }
    }

    @Transactional
    public boolean hasScope(Long userId, String scopeCode) {
        expireDuePurchasesForUser(userId);
        repairUsablePackageEntitlements(userId);
        List<UserEntitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);
        Map<String, Product> productsByCode = productRepository.findByCodeIn(
                entitlements.stream().map(UserEntitlement::getProductCode).distinct().toList()
        ).stream().collect(Collectors.toMap(Product::getCode, Function.identity(), (left, right) -> left));

        return entitlements.stream()
                .anyMatch(entitlement -> {
                    Product product = productsByCode.get(entitlement.getProductCode());
                    if (product == null || !Objects.equals(product.getScopeCode(), scopeCode)) {
                        return false;
                    }
                    Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
                    return purchase != null && entitlement.grantsScope(purchase);
                });
    }

    @Transactional
    public void requireScope(Long userId, String scopeCode) {
        if (!hasScope(userId, scopeCode)) {
            throw new ForbiddenException(scopeCode + " yetkisi için uygun paket gerekli");
        }
    }

    @Transactional
    public boolean hasUsableQrCreatePackage(Long userId) {
        expireDuePurchasesForUser(userId);
        List<UserEntitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);
        return entitlements.stream()
                .filter(entitlement -> Objects.equals(entitlement.getProductCode(), CatalogProducts.QR_CREATE))
                .anyMatch(entitlement -> {
                    Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
                    return purchase != null && purchase.isUsable();
                });
    }

    @Transactional
    public List<UserEntitlementResponse> getUserEntitlements(Long userId) {
        expireDuePurchasesForUser(userId);
        repairUsablePackageEntitlements(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(
                entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)
        );

        return entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(entitlement -> toResponse(entitlement, purchasesById.get(entitlement.getPurchaseId())))
                .toList();
    }

    @Transactional
    public void repairUsablePackageEntitlements(Long userId) {
        List<Purchase> usablePurchases = purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.FREE)
                .filter(purchase -> !CatalogPackages.FREE_PACKAGE.equals(purchase.getPackageCode()))
                .toList();
        for (Purchase purchase : usablePurchases) {
            if (purchase.getPackageId() == null) {
                continue;
            }
            planPackageRepository.findByIdWithItems(purchase.getPackageId())
                    .ifPresent(planPackage -> ensureEntitlementsForPackage(purchase, planPackage));
        }
        syncQrMenuUsageFromActiveMenus(userId);
        syncQrCreateUsageFromActiveQrs(userId);
        syncMenuProductUsageFromActiveProducts(userId);
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
                .filter(purchase -> !CatalogPackages.FREE_PACKAGE.equals(purchase.getPackageCode()))
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

    private void syncQrCreateUsageFromActiveQrs(Long userId) {
        if (userId == null) {
            return;
        }

        List<UserEntitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);

        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), CatalogProducts.QR_CREATE) || entitlement.isUnlimited()) {
                continue;
            }
            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            if (purchase == null || !entitlement.isUsable(purchase)) {
                continue;
            }

            long activeQrCount = qrRepository.countByUserIdAndPurchaseIdAndDeletedFalse(
                    userId,
                    entitlement.getPurchaseId()
            );
            int total = entitlement.getTotalQuantity() != null ? entitlement.getTotalQuantity() : 0;
            int used = (int) Math.min(activeQrCount, total);
            entitlement.setUsedQuantity(used);
            entitlement.setRemainingQuantity(Math.max(0, total - used));
            entitlementRepository.save(entitlement);
        }
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
        List<UserEntitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);
        int remaining = 0;
        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), CatalogProducts.QR_MENU) || entitlement.isUnlimited()) {
                continue;
            }
            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            if (purchase == null || !entitlement.isUsable(purchase)) {
                continue;
            }
            remaining += Math.max(0, entitlement.getRemainingQuantity());
        }
        return remaining;
    }

    private void syncQrMenuUsageFromActiveMenus(Long userId) {
        if (userId == null) {
            return;
        }

        long activeMenus = menuRepository.countActiveLiveMenusForUser(userId);
        List<UserEntitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);

        int remainingActive = (int) Math.min(activeMenus, Integer.MAX_VALUE);
        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), CatalogProducts.QR_MENU) || entitlement.isUnlimited()) {
                continue;
            }
            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            if (purchase == null || !entitlement.isUsable(purchase)) {
                continue;
            }

            int total = entitlement.getTotalQuantity() != null ? entitlement.getTotalQuantity() : 0;
            int used = Math.min(total, remainingActive);
            entitlement.setUsedQuantity(used);
            entitlement.setRemainingQuantity(Math.max(0, total - used));
            entitlementRepository.save(entitlement);
            remainingActive -= used;
        }
    }

    @Transactional(readOnly = true)
    public List<UserEntitlementResponse> getPurchaseEntitlements(Purchase purchase) {
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
        List<UserEntitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);
        int remaining = 0;
        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), CatalogProducts.MENU_PRODUCT) || entitlement.isUnlimited()) {
                continue;
            }
            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            if (purchase == null || !entitlement.isUsable(purchase)) {
                continue;
            }
            remaining += Math.max(0, entitlement.getRemainingQuantity());
        }
        return remaining;
    }

    private void syncMenuProductUsageFromActiveProducts(Long userId) {
        if (userId == null) {
            return;
        }

        long activeProducts = menuProductRepository.countActiveProductsForUser(userId);
        List<UserEntitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtAsc(userId);
        Map<Long, Purchase> purchasesById = loadPurchases(entitlements);

        int remainingActive = (int) Math.min(activeProducts, Integer.MAX_VALUE);
        for (UserEntitlement entitlement : entitlements) {
            if (!Objects.equals(entitlement.getProductCode(), CatalogProducts.MENU_PRODUCT) || entitlement.isUnlimited()) {
                continue;
            }
            Purchase purchase = purchasesById.get(entitlement.getPurchaseId());
            if (purchase == null || !entitlement.isUsable(purchase)) {
                continue;
            }

            int total = entitlement.getTotalQuantity() != null ? entitlement.getTotalQuantity() : 0;
            int used = Math.min(total, remainingActive);
            entitlement.setUsedQuantity(used);
            entitlement.setRemainingQuantity(Math.max(0, total - used));
            entitlementRepository.save(entitlement);
            remainingActive -= used;
        }
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

    private Map<Long, Purchase> loadPurchases(List<UserEntitlement> entitlements) {
        List<Long> purchaseIds = entitlements.stream()
                .map(UserEntitlement::getPurchaseId)
                .distinct()
                .toList();

        return purchaseRepository.findAllById(purchaseIds).stream()
                .collect(Collectors.toMap(Purchase::getId, Function.identity()));
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
