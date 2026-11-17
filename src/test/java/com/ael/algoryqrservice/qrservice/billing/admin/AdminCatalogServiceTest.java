package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageItemRequest;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageRequest;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageResponse;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.ProductRequest;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.ProductResponse;
import com.ael.algoryqrservice.qrservice.billing.service.PackagePricingService;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ConflictException;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.qrservice.model.enums.ProductType;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageItemRepository;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCatalogServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final PlanPackageRepository packages = mock(PlanPackageRepository.class);
    private final PlanPackageItemRepository items = mock(PlanPackageItemRepository.class);
    private final AdminCatalogService service =
            new AdminCatalogService(products, packages, items, new PackagePricingService(),
                    org.mockito.Mockito.mock(PlanVersionService.class), org.mockito.Mockito.mock(FeatureLabels.class));

    private final Product menu = product(1L, "QR_MENU", "100");
    private final Product branch = product(2L, "QR_BRANCH", "50");
    private final Product archived = product(3L, "OLD_THING", "10");
    private final List<PlanPackage> catalog = new ArrayList<>();

    @BeforeEach
    void setUp() {
        archived.setActive(false);
        Map<Long, Product> byId = List.of(menu, branch, archived).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        when(products.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(byId.get(inv.<Long>getArgument(0))));
        when(products.findAllById(anyCollection())).thenAnswer(inv -> inv.<Collection<Long>>getArgument(0).stream()
                .map(byId::get).filter(p -> p != null).toList());
        when(products.findByCode(any())).thenReturn(Optional.empty());
        when(products.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(packages.findByCode(any())).thenReturn(Optional.empty());
        when(packages.save(any(PlanPackage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(packages.findAll()).thenReturn(catalog);
        when(packages.findAllByIdWithItems(any())).thenAnswer(inv -> catalog.stream()
                .filter(p -> inv.<List<Long>>getArgument(0).contains(p.getId())).toList());
        when(packages.findByIdWithItems(anyLong())).thenAnswer(inv -> catalog.stream()
                .filter(p -> p.getId().equals(inv.getArgument(0))).findFirst());
    }

    // ---- products ----

    @Test
    void newProductUsesItsCodeAsFeatureCodeWhenNoneGiven() {
        ProductResponse created = service.createProduct(productRequest("waiter_panel", null, true));

        assertThat(created.code()).isEqualTo("WAITER_PANEL");
        assertThat(created.featureCode()).isEqualTo("WAITER_PANEL");
        assertThat(created.priceWithVat()).isEqualByComparingTo("60.00");
    }

    @Test
    void duplicateProductCodeIsRejected() {
        when(products.findByCode("QR_MENU")).thenReturn(Optional.of(menu));

        assertThatThrownBy(() -> service.createProduct(productRequest("QR_MENU", null, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void consumableAddonIsAlwaysSoldOnce() {
        ProductRequest request = new ProductRequest("REPORT_QUOTA", "Rapor hakkı", null, ProductType.ADDON_PRODUCT,
                "SMART_REPORT_QUOTA", null, new BigDecimal("25"), null, ProductBillingType.RECURRING, true, true, true);

        assertThat(service.createProduct(request).billingType()).isEqualTo(ProductBillingType.ONE_TIME);
    }

    @Test
    void featureCodeNothingChecksIsRejected() {
        assertThatThrownBy(() -> service.createProduct(productRequest("KITCHEN_DISPLAY", null, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tanımsız hak kodu");
        verify(products, never()).save(any());
    }

    @Test
    void scopeCodeNothingChecksIsRejected() {
        ProductRequest request = new ProductRequest("QR_MENU_PLUS", "Ürün", null, ProductType.PACKAGE_PRODUCT,
                "QR_MENU", "qr_menu_admin", new BigDecimal("50"), new BigDecimal("20"),
                ProductBillingType.RECURRING, false, false, true);

        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tanımsız panel yetkisi");
    }

    @Test
    void legacyProductKeepsItsUnknownCodesWhenEdited() {
        archived.setScopeCode("OLD_THING_OWNER");
        archived.setActive(true);
        ProductRequest request = new ProductRequest("OLD_THING", "Eski", null, ProductType.PACKAGE_PRODUCT,
                "OLD_THING", "OLD_THING_OWNER", new BigDecimal("12"), new BigDecimal("20"),
                ProductBillingType.RECURRING, false, false, true);

        assertThat(service.updateProduct(3L, request).unitPrice()).isEqualByComparingTo("12.00");
    }

    @Test
    void productCodeCannotChange() {
        assertThatThrownBy(() -> service.updateProduct(1L, productRequest("QR_MENU_2", "QR_MENU", true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("değiştirilemez");
    }

    @Test
    void featureCodeOfAProductInPackagesCannotChange() {
        when(items.existsByProductId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProduct(1L, productRequest("QR_MENU", "QR_BRANCH", true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Paketlerde kullanılan");
    }

    @Test
    void productInAnActivePackageCannotBeArchived() {
        catalog.add(pkg(10L, "PRO_PACKAGE", true, line(menu, 1, false)));

        assertThatThrownBy(() -> service.updateProduct(1L, productRequest("QR_MENU", "QR_MENU", false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PRO_PACKAGE");
        verify(products, never()).save(any());
    }

    @Test
    void productUsedOnlyByArchivedPackagesCanBeArchived() {
        catalog.add(pkg(10L, "OLD_PACKAGE", false, line(menu, 1, false)));

        assertThat(service.updateProduct(1L, productRequest("QR_MENU", "QR_MENU", false)).active()).isFalse();
    }

    @Test
    void priceChangeRepricesPackagesThatContainTheProduct() {
        PlanPackage pro = pkg(10L, "PRO_PACKAGE", true, line(menu, 1, false), line(branch, 2, false));
        new PackagePricingService().applyTo(pro);
        catalog.add(pro);
        ProductRequest cheaper = new ProductRequest("QR_MENU", "QR Menü", null, ProductType.PACKAGE_PRODUCT,
                "QR_MENU", null, new BigDecimal("40"), new BigDecimal("20"), ProductBillingType.RECURRING,
                false, false, true);

        service.updateProduct(1L, cheaper);

        // (40 + 2 × 50) × 1.20
        assertThat(pro.getPrice()).isEqualByComparingTo("168.00");
    }

    // ---- packages ----

    @Test
    void packagePriceIsTheSumOfItsLinesAndUnlimitedCountsOnce() {
        PackageResponse created = service.createPackage(packageRequest("pro_package", true, true,
                new PackageItemRequest(1L, 1, false), new PackageItemRequest(2L, null, true)));

        // (100 + 50) × 1.20
        assertThat(created.price()).isEqualByComparingTo("180.00");
        assertThat(created.systemManaged()).isFalse();
        assertThat(created.items()).extracting(i -> i.unlimited()).containsExactlyInAnyOrder(false, true);
    }

    @Test
    void sameProductTwiceIsRejected() {
        assertThatThrownBy(() -> service.createPackage(packageRequest("P1", true, true,
                new PackageItemRequest(1L, 1, false), new PackageItemRequest(1L, 2, false))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void countedLineNeedsAtLeastOne() {
        assertThatThrownBy(() -> service.createPackage(packageRequest("P1", true, true,
                new PackageItemRequest(1L, 0, false))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void activePackageCannotContainArchivedProduct() {
        assertThatThrownBy(() -> service.createPackage(packageRequest("P1", true, true,
                new PackageItemRequest(1L, 1, false), new PackageItemRequest(3L, 1, false))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("OLD_THING");
    }

    @Test
    void discountCannotExceedPrice() {
        PackageRequest request = new PackageRequest("P1", "P1", null, List.of(), new BigDecimal("500"), null, null,
                "TRY", 30, 0, true, true, List.of(new PackageItemRequest(1L, 1, false)));

        assertThatThrownBy(() -> service.createPackage(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void freePackageCannotBeSold() {
        Product free = product(4L, "FREE_THING", "0");
        when(products.findAllById(anyCollection())).thenReturn(List.of(free));

        assertThatThrownBy(() -> service.createPackage(packageRequest("P1", true, true,
                new PackageItemRequest(4L, 1, false))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ücretsiz");
    }

    @Test
    void systemPackageStaysActiveAndUnsold() {
        PlanPackage trial = pkg(20L, "ULTIMATE_TRIAL_PACKAGE", true, line(menu, 1, false));
        trial.setSystemManaged(true);
        catalog.add(trial);

        assertThatThrownBy(() -> service.updatePackage(20L, packageRequest("ULTIMATE_TRIAL_PACKAGE", true, true,
                new PackageItemRequest(1L, 1, false))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.updatePackage(20L, packageRequest("ULTIMATE_TRIAL_PACKAGE", false, false,
                new PackageItemRequest(1L, 1, false))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void editingItemsKeepsUnchangedLinesAndDropsRemovedOnes() {
        PlanPackageItem keep = line(menu, 1, false);
        PlanPackage pro = pkg(10L, "PRO_PACKAGE", true, keep, line(branch, 1, false));
        catalog.add(pro);

        service.updatePackage(10L, packageRequest("PRO_PACKAGE", true, true, new PackageItemRequest(1L, 3, false)));

        assertThat(pro.getItems()).containsExactly(keep);
        assertThat(keep.getQuantity()).isEqualTo(3);
        assertThat(pro.getPrice()).isEqualByComparingTo("360.00");
    }

    @Test
    void packageCodeCannotChange() {
        catalog.add(pkg(10L, "PRO_PACKAGE", true, line(menu, 1, false)));

        assertThatThrownBy(() -> service.updatePackage(10L, packageRequest("PRO_2", true, true,
                new PackageItemRequest(1L, 1, false))))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- fixtures ----

    private static Product product(Long id, String code, String unitPrice) {
        Product product = new Product();
        product.setId(id);
        product.setCode(code);
        product.setName(code);
        product.setFeatureCode(code);
        product.setTypeId(ProductType.PACKAGE_PRODUCT);
        product.setBillingType(ProductBillingType.RECURRING);
        product.setUnitPrice(new BigDecimal(unitPrice));
        product.setVatRate(new BigDecimal("20"));
        product.setActive(true);
        return product;
    }

    private static PlanPackageItem line(Product product, int quantity, boolean unlimited) {
        PlanPackageItem item = new PlanPackageItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnlimited(unlimited);
        return item;
    }

    private static PlanPackage pkg(Long id, String code, boolean active, PlanPackageItem... lines) {
        PlanPackage planPackage = new PlanPackage();
        planPackage.setId(id);
        planPackage.setCode(code);
        planPackage.setName(code);
        planPackage.setActive(active);
        planPackage.setItems(new ArrayList<>());
        for (PlanPackageItem line : lines) {
            line.setPlanPackage(planPackage);
            planPackage.getItems().add(line);
        }
        return planPackage;
    }

    private static ProductRequest productRequest(String code, String featureCode, boolean active) {
        return new ProductRequest(code, "Ürün", null, ProductType.PACKAGE_PRODUCT, featureCode, null,
                new BigDecimal("50"), new BigDecimal("20"), ProductBillingType.RECURRING, false, false, active);
    }

    private static PackageRequest packageRequest(String code, boolean active, boolean purchasable,
                                                 PackageItemRequest... lines) {
        return new PackageRequest(code, "Paket", null, List.of("Özellik"), null, null, null, "TRY", 30, 0,
                purchasable, active, List.of(lines));
    }
}
