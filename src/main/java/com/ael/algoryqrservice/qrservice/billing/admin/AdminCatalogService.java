package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageItemRequest;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageItemResponse;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageRequest;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageResponse;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.ProductRequest;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.ProductResponse;
import com.ael.algoryqrservice.qrservice.billing.service.PackagePricingService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ConflictException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageItemRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalog management for the admin panel. Rules that keep live subscriptions safe:
 * <ul>
 *   <li>codes never change once created (entitlements, purchases and code refer to them);</li>
 *   <li>nothing is deleted: products and packages are archived by turning {@code active} off;</li>
 *   <li>a product still used by an active package cannot be archived;</li>
 *   <li>system packages (free, trial) stay active and are never sold;</li>
 *   <li>price changes apply to new purchases and to existing subscriptions only through a plan
 *       change, because each subscription keeps the amount it was sold at.</li>
 * </ul>
 * Package contents are read again whenever a package is fulfilled, so a changed package reaches
 * existing subscribers at their next renewal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCatalogService {

    private final ProductRepository productRepository;
    private final PlanPackageRepository planPackageRepository;
    private final PlanPackageItemRepository planPackageItemRepository;
    private final PackagePricingService pricing;

    // ---- products ----

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        return productRepository.findAll(Sort.by("code")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return toResponse(findProduct(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String code = normalizeCode(request.code());
        if (productRepository.findByCode(code).isPresent()) {
            throw new ConflictException("PRODUCT_CODE_TAKEN", "Bu kodla bir ürün zaten var: " + code);
        }
        Product product = new Product();
        product.setCode(code);
        apply(product, request);
        product.setFeatureCode(featureCodeOf(request, code));
        Product saved = productRepository.save(product);
        log.info("admin_catalog product_created code={} feature={}", saved.getCode(), saved.getFeatureCode());
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProduct(id);
        if (!product.getCode().equals(normalizeCode(request.code()))) {
            throw new BadRequestException("PRODUCT_CODE_IMMUTABLE", "Ürün kodu değiştirilemez");
        }
        String featureCode = featureCodeOf(request, product.getCode());
        if (!Objects.equals(product.getFeatureCode(), featureCode)
                && product.getFeatureCode() != null
                && planPackageItemRepository.existsByProductId(product.getId())) {
            throw new BadRequestException("PRODUCT_FEATURE_IN_USE",
                    "Paketlerde kullanılan bir ürünün hak kodu değiştirilemez");
        }
        if (product.isActive() && !request.active()) {
            List<String> packages = activePackagesUsing(product.getId());
            if (!packages.isEmpty()) {
                throw new BadRequestException("PRODUCT_IN_ACTIVE_PACKAGE",
                        "Ürün aktif paketlerde kullanılıyor: " + String.join(", ", packages));
            }
        }
        apply(product, request);
        product.setFeatureCode(featureCode);
        Product saved = productRepository.save(product);
        // Package prices are the sum of their lines: keep them in step with the new unit price.
        repriceSalesOf(saved.getId());
        log.info("admin_catalog product_updated code={} active={} unitPrice={}",
                saved.getCode(), saved.isActive(), saved.getUnitPrice());
        return toResponse(saved);
    }

    // ---- packages ----

    @Transactional(readOnly = true)
    public List<PackageResponse> listPackages() {
        List<Long> ids = planPackageRepository.findAll().stream().map(PlanPackage::getId).toList();
        return planPackageRepository.findAllByIdWithItems(ids).stream()
                .sorted(Comparator.comparing((PlanPackage p) -> !p.isActive())
                        .thenComparing(p -> p.getPriority() == null ? 0 : -p.getPriority())
                        .thenComparing(PlanPackage::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PackageResponse getPackage(Long id) {
        return toResponse(findPackage(id));
    }

    @Transactional
    public PackageResponse createPackage(PackageRequest request) {
        String code = normalizeCode(request.code());
        if (planPackageRepository.findByCode(code).isPresent()) {
            throw new ConflictException("PACKAGE_CODE_TAKEN", "Bu kodla bir paket zaten var: " + code);
        }
        PlanPackage planPackage = new PlanPackage();
        planPackage.setCode(code);
        planPackage.setSystemManaged(false);
        apply(planPackage, request);
        PlanPackage saved = planPackageRepository.save(planPackage);
        log.info("admin_catalog package_created code={} price={} items={}",
                saved.getCode(), saved.getPrice(), saved.getItems().size());
        return toResponse(saved);
    }

    @Transactional
    public PackageResponse updatePackage(Long id, PackageRequest request) {
        PlanPackage planPackage = findPackage(id);
        if (!planPackage.getCode().equals(normalizeCode(request.code()))) {
            throw new BadRequestException("PACKAGE_CODE_IMMUTABLE", "Paket kodu değiştirilemez");
        }
        if (planPackage.isSystemManaged() && (!request.active() || request.purchasable())) {
            throw new BadRequestException("PACKAGE_SYSTEM_MANAGED",
                    "Sistem paketi (ücretsiz/deneme) her zaman aktif kalır ve satışa açılamaz");
        }
        apply(planPackage, request);
        PlanPackage saved = planPackageRepository.save(planPackage);
        log.info("admin_catalog package_updated code={} active={} price={} items={}",
                saved.getCode(), saved.isActive(), saved.getPrice(), saved.getItems().size());
        return toResponse(saved);
    }

    // ---- internals ----

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(blankToNull(request.description()));
        product.setTypeId(request.type());
        product.setScopeCode(blankToNull(request.scopeCode()));
        product.setUnitPrice(money(request.unitPrice()));
        product.setVatRate(request.vatRate() == null
                ? PackagePricingService.DEFAULT_VAT_RATE
                : request.vatRate().setScale(2, RoundingMode.HALF_UP));
        product.setBillingType(request.billingType());
        product.setConsumable(request.consumable());
        // A consumable credit is used up, so it is never billed again on renewal.
        if (request.consumable() && request.billingType() == ProductBillingType.RECURRING && request.addonPurchasable()) {
            product.setBillingType(ProductBillingType.ONE_TIME);
        }
        product.setAddonPurchasable(request.addonPurchasable());
        product.setRequiresCountSync(request.requiresCountSync());
        product.setActive(request.active());
    }

    private void apply(PlanPackage planPackage, PackageRequest request) {
        planPackage.setName(request.name().trim());
        planPackage.setDescription(blankToNull(request.description()));
        planPackage.setFeatures(request.features() == null ? new ArrayList<>() : request.features().stream()
                .map(String::trim).filter(f -> !f.isEmpty()).collect(Collectors.toCollection(ArrayList::new)));
        planPackage.setCurrency(request.currency() == null ? "TRY" : request.currency().toUpperCase(Locale.ROOT));
        planPackage.setValidityDays(request.validityDays());
        planPackage.setPriority(request.priority() == null ? 0 : request.priority());
        planPackage.setPurchasable(request.purchasable());
        planPackage.setActive(request.active());
        replaceItems(planPackage, request.items(), request.active());

        pricing.applyTo(planPackage);
        BigDecimal monthlyDiscount = money(request.monthlyDiscount());
        if (monthlyDiscount.compareTo(planPackage.getPrice()) > 0) {
            throw new BadRequestException("PACKAGE_DISCOUNT_TOO_HIGH", "Aylık indirim paket fiyatını aşamaz");
        }
        planPackage.setMonthlyDiscount(monthlyDiscount);
        if (request.yearlyPrice() == null || request.yearlyPrice().signum() == 0) {
            planPackage.setYearlyPrice(null);
            planPackage.setYearlyDiscount(BigDecimal.ZERO);
        } else {
            BigDecimal yearlyDiscount = money(request.yearlyDiscount());
            if (yearlyDiscount.compareTo(request.yearlyPrice()) > 0) {
                throw new BadRequestException("PACKAGE_DISCOUNT_TOO_HIGH", "Yıllık indirim yıllık fiyatı aşamaz");
            }
            planPackage.setYearlyPrice(money(request.yearlyPrice()));
            planPackage.setYearlyDiscount(yearlyDiscount);
        }
        if (planPackage.isPurchasable() && planPackage.effectiveMonthlyPrice().signum() <= 0) {
            throw new BadRequestException("PACKAGE_FREE_PURCHASABLE",
                    "Ücretsiz bir paket satışa açılamaz; satın alınamaz olarak işaretleyin");
        }
    }

    /** Edits lines in place so unchanged lines keep their ids. */
    private void replaceItems(PlanPackage planPackage, List<PackageItemRequest> requested, boolean packageActive) {
        Set<Long> seen = new HashSet<>();
        for (PackageItemRequest line : requested) {
            if (!seen.add(line.productId())) {
                throw new BadRequestException("PACKAGE_DUPLICATE_PRODUCT", "Aynı ürün pakete iki kez eklenemez");
            }
            if (!line.unlimited() && (line.quantity() == null || line.quantity() < 1)) {
                throw new BadRequestException("PACKAGE_ITEM_QUANTITY", "Sınırsız olmayan satırlarda adet en az 1 olmalı");
            }
        }
        Map<Long, Product> products = productRepository.findAllById(seen).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : seen) {
            Product product = products.get(productId);
            if (product == null) {
                throw new BadRequestException("PACKAGE_UNKNOWN_PRODUCT", "Ürün bulunamadı: " + productId);
            }
            if (packageActive && !product.isActive()) {
                throw new BadRequestException("PACKAGE_INACTIVE_PRODUCT",
                        "Arşivlenmiş ürün aktif pakete eklenemez: " + product.getCode());
            }
        }
        Map<Long, PlanPackageItem> existing = planPackage.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity(), (a, b) -> a));
        planPackage.getItems().removeIf(item -> !seen.contains(item.getProduct().getId()));
        for (PackageItemRequest line : requested) {
            PlanPackageItem item = existing.get(line.productId());
            if (item == null) {
                item = new PlanPackageItem();
                item.setPlanPackage(planPackage);
                item.setProduct(products.get(line.productId()));
                planPackage.getItems().add(item);
            }
            item.setUnlimited(line.unlimited());
            item.setQuantity(line.unlimited() ? 1 : line.quantity());
        }
    }

    private void repriceSalesOf(Long productId) {
        List<Long> packageIds = planPackageRepository.findAll().stream()
                .filter(p -> p.getItems().stream().anyMatch(i -> i.getProduct().getId().equals(productId)))
                .map(PlanPackage::getId)
                .toList();
        for (PlanPackage planPackage : planPackageRepository.findAllByIdWithItems(packageIds)) {
            pricing.applyTo(planPackage);
            planPackageRepository.save(planPackage);
        }
    }

    private List<String> activePackagesUsing(Long productId) {
        return planPackageRepository.findAll().stream()
                .filter(PlanPackage::isActive)
                .filter(p -> p.getItems().stream().anyMatch(i -> i.getProduct().getId().equals(productId)))
                .map(PlanPackage::getCode)
                .toList();
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Ürün bulunamadı: " + id));
    }

    private PlanPackage findPackage(Long id) {
        return planPackageRepository.findByIdWithItems(id)
                .orElseThrow(() -> new NotFoundException("Paket bulunamadı: " + id));
    }

    private ProductResponse toResponse(Product product) {
        PackagePricingService.LinePrice price = pricing.calculateProduct(product, 1);
        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getDescription(),
                product.getTypeId(),
                product.getFeatureCode(),
                product.getScopeCode(),
                product.getUnitPrice(),
                product.getVatRate(),
                price.lineTotal(),
                product.getBillingType(),
                product.isConsumable(),
                product.isAddonPurchasable(),
                product.isRequiresCountSync(),
                product.isActive(),
                planPackageItemRepository.countByProductId(product.getId()),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private PackageResponse toResponse(PlanPackage planPackage) {
        List<PackageItemResponse> items = planPackage.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getCode()))
                .map(item -> new PackageItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getCode(),
                        item.getProduct().getName(),
                        item.getProduct().getFeatureCode(),
                        item.getProduct().getTypeId(),
                        item.getQuantity(),
                        item.isUnlimited(),
                        pricing.calculateProduct(item.getProduct(), item.isUnlimited() ? 1 : item.getQuantity())
                                .lineTotal()))
                .toList();
        return new PackageResponse(
                planPackage.getId(),
                planPackage.getCode(),
                planPackage.getName(),
                planPackage.getDescription(),
                planPackage.getFeatures(),
                planPackage.getSubtotal(),
                planPackage.getVatAmount(),
                planPackage.getPrice(),
                planPackage.getMonthlyDiscount(),
                planPackage.effectiveMonthlyPrice(),
                planPackage.getYearlyPrice(),
                planPackage.getYearlyDiscount(),
                planPackage.effectiveYearlyPrice(),
                planPackage.getCurrency(),
                planPackage.getValidityDays(),
                planPackage.getPriority(),
                planPackage.isPurchasable(),
                planPackage.isActive(),
                planPackage.isSystemManaged(),
                items,
                planPackage.getCreatedAt(),
                planPackage.getUpdatedAt()
        );
    }

    private static String featureCodeOf(ProductRequest request, String productCode) {
        String feature = blankToNull(request.featureCode());
        return feature == null ? productCode : normalizeCode(feature);
    }

    private static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
