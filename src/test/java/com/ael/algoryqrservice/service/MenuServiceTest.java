package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuAllergen;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuTag;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.model.enums.NutritionBasis;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.model.nutrition.NutritionNutrientEntry;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private MenuTaxonomyService menuTaxonomyService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;
    @Mock
    private NutritionFactsService nutritionFactsService;
    @Spy
    private ServesPeopleSupport servesPeopleSupport = new ServesPeopleSupport();
    @Mock
    private QrRepository qrRepository;
    @Mock
    private QrGenerationService qrGenerationService;
    @Mock
    private AppProperties appProperties;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private ProductImageStorageService productImageStorageService;
    @Mock
    private ChefAvatarService chefAvatarService;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private MenuQrSoftDeleteService menuQrSoftDeleteService;
    @Mock
    private BranchService branchService;
    @Mock
    private BranchQuotaService branchQuotaService;
    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private MenuService menuService;

    @BeforeEach
    void stubCurrentUser() {
        org.mockito.Mockito.lenient().when(securityUtils.getCurrentUserId()).thenReturn(7L);
        org.mockito.Mockito.lenient().when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(7L).build());
    }

    @Test
    void createMenuForQr_whenSloganAndProductsProvided_thenPersistAll() {
        Qr qr = Qr.builder().qrId(42L).userId(7L).build();
        Map<String, Object> details = new HashMap<>();
        details.put("themeId", "soft");
        details.put("branchId", 3L);
        details.put("businessName", "Kafe İstanbul");
        details.put("slogan", "Lezzetin adresi");
        Map<String, Object> nutrition = Map.of(
                "basis", "PER_100G",
                "energyKj", 850,
                "energyKcal", 203,
                "fat", 10.5,
                "carbohydrate", 25,
                "fibre", 2.1,
                "protein", 8,
                "salt", 1.2
        );
        details.put("products", List.of(
                Map.of(
                        "name", "Espresso",
                        "subCategoryId", 1,
                        "price", "120",
                        "description", "Tek shot",
                        "nutrition", nutrition
                )
        ));
        QrRequest request = new QrRequest();
        request.setDetails(details);
        NutritionFacts parsedNutrition = sampleNutrition();
        SubCategory sub = SubCategory.builder().id(1L).mainCategoryId(1L).slug("sicak_icecekler").name("Sıcak İçecekler").sortOrder(1).build();

        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            menu.setMenuId(99L);
            return menu;
        });
        when(menuTaxonomyService.requireSubCategory(1L)).thenReturn(sub);
        when(menuTaxonomyService.requireTags(any())).thenReturn(List.of());
        when(menuTaxonomyService.requireAllergens(any())).thenReturn(List.of());
        when(menuTaxonomyService.findTagBySlug(any())).thenReturn(Optional.empty());
        when(nutritionFactsService.parseFromRaw(nutrition)).thenReturn(parsedNutrition);
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(entitlementService).assertMenuProductCreationAllowed(7L, 1);
        doNothing().when(entitlementService).syncMenuProductEntitlements(7L);

        Menu saved = menuService.createMenuForQr(qr, request);

        assertThat(saved.getBusinessName()).isEqualTo("Kafe İstanbul");
        assertThat(saved.getSlogan()).isEqualTo("Lezzetin adresi");
        assertThat(saved.getMenuId()).isEqualTo(99L);
        verify(menuPublicAccessService).syncForUser(7L);
        verify(nutritionFactsService).validateForCreate(parsedNutrition);

        ArgumentCaptor<MenuProduct> productCaptor = ArgumentCaptor.forClass(MenuProduct.class);
        verify(menuProductRepository, times(1)).save(productCaptor.capture());
        MenuProduct product = productCaptor.getValue();
        assertThat(product.getMenuId()).isEqualTo(99L);
        assertThat(product.getName()).isEqualTo("Espresso");
        assertThat(product.getSubCategoryId()).isEqualTo(1L);
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(product.getDescription()).isEqualTo("Tek shot");
        assertThat(product.getNutrition()).isEqualTo(parsedNutrition);
    }

    @Test
    void createMenuForQr_whenProductMissingSubCategoryId_thenThrow() {
        Qr qr = Qr.builder().qrId(42L).userId(7L).build();
        Map<String, Object> details = new HashMap<>();
        details.put("themeId", "soft");
        details.put("branchId", 3L);
        details.put("businessName", "Kafe");
        details.put("products", List.of(Map.of("name", "Espresso", "nutrition", Map.of("basis", "PER_100G"))));
        QrRequest request = new QrRequest();
        request.setDetails(details);

        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            menu.setMenuId(99L);
            return menu;
        });

        assertThatThrownBy(() -> menuService.createMenuForQr(qr, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("subCategoryId");
    }

    @Test
    void createMenuForQr_whenSourceMenuIdProvided_thenCopyProducts() {
        Qr qr = Qr.builder().qrId(50L).userId(7L).build();
        Map<String, Object> details = new HashMap<>();
        details.put("themeId", "soft");
        details.put("branchId", 3L);
        details.put("businessName", "Yeni Şube");
        details.put("sourceMenuId", 12L);
        QrRequest request = new QrRequest();
        request.setDetails(details);

        Menu sourceMenu = Menu.builder().menuId(12L).userId(7L).active(true).deleted(false).build();
        MenuProduct sourceProduct = MenuProduct.builder()
                .productId(100L)
                .menuId(12L)
                .name("Latte")
                .description("Sütlü kahve")
                .price(new BigDecimal("150"))
                .currency("TRY")
                .subCategoryId(3L)
                .sortOrder(0)
                .available(true)
                .chefRecommended(true)
                .tagIds(Set.of(8L))
                .allergenIds(Set.of(2L))
                .nutrition(sampleNutrition())
                .build();

        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            menu.setMenuId(99L);
            return menu;
        });
        when(menuRepository.findById(12L)).thenReturn(Optional.of(sourceMenu));
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(12L))
                .thenReturn(List.of(sourceProduct));
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(entitlementService).assertMenuProductCreationAllowed(7L, 1);
        doNothing().when(entitlementService).syncMenuProductEntitlements(7L);

        Menu saved = menuService.createMenuForQr(qr, request);

        assertThat(saved.getBusinessName()).isEqualTo("Yeni Şube");
        verify(entitlementService).assertMenuProductCreationAllowed(7L, 1);

        ArgumentCaptor<MenuProduct> productCaptor = ArgumentCaptor.forClass(MenuProduct.class);
        verify(menuProductRepository, times(1)).save(productCaptor.capture());
        MenuProduct copied = productCaptor.getValue();
        assertThat(copied.getMenuId()).isEqualTo(99L);
        assertThat(copied.getName()).isEqualTo("Latte");
        assertThat(copied.getSubCategoryId()).isEqualTo(3L);
        assertThat(copied.getTagIds()).containsExactly(8L);
        assertThat(copied.getAllergenIds()).containsExactly(2L);
        assertThat(copied.getRatingAvg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(copied.getRatingCount()).isZero();
    }

    @Test
    void createMenuForQr_whenSourceMenuNotOwned_thenThrow() {
        Qr qr = Qr.builder().qrId(50L).userId(7L).build();
        Map<String, Object> details = new HashMap<>();
        details.put("themeId", "soft");
        details.put("branchId", 3L);
        details.put("businessName", "Yeni Şube");
        details.put("sourceMenuId", 12L);
        QrRequest request = new QrRequest();
        request.setDetails(details);

        Menu sourceMenu = Menu.builder().menuId(12L).userId(99L).active(true).deleted(false).build();

        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            menu.setMenuId(99L);
            return menu;
        });
        when(menuRepository.findById(12L)).thenReturn(Optional.of(sourceMenu));

        assertThatThrownBy(() -> menuService.createMenuForQr(qr, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("erişim");
    }

    @Test
    void createProduct_whenChefRecommendedTrue_thenSetFlagAndTag() {
        Menu menu = Menu.builder().menuId(10L).userId(7L).build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        when(menuTaxonomyService.requireSubCategory(16L))
                .thenReturn(SubCategory.builder().id(16L).mainCategoryId(5L).slug("et_yemekleri").name("Et").sortOrder(1).build());
        when(menuTaxonomyService.findTagBySlug(MenuTaxonomyService.CHEF_RECOMMENDED_TAG_SLUG))
                .thenReturn(Optional.of(MenuTag.builder().id(8L).slug("sef_ozel").name("Şef Özel").sortOrder(8).build()));
        when(menuTaxonomyService.requireTags(any())).thenReturn(List.of());
        when(menuTaxonomyService.requireAllergens(any())).thenReturn(List.of());
        doNothing().when(nutritionFactsService).validateForCreate(any());
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(10L))
                .thenReturn(List.of());
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> {
            MenuProduct saved = invocation.getArgument(0);
            saved.setProductId(55L);
            return saved;
        });
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of(
                16L, SubCategory.builder().id(16L).mainCategoryId(5L).slug("et_yemekleri").name("Et").sortOrder(1).build()
        ));
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of(
                8L, MenuTag.builder().id(8L).slug("sef_ozel").name("Şef Özel").sortOrder(8).build()
        ));
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of());

        MenuDtos.MenuProductRequest request = MenuDtos.MenuProductRequest.builder()
                .name("Şef Köfte")
                .subCategoryId(16L)
                .chefRecommended(true)
                .nutrition(sampleNutrition())
                .build();

        MenuDtos.MenuProductResponse response = menuService.createProduct(10L, request);

        assertThat(response.isChefRecommended()).isTrue();
        ArgumentCaptor<MenuProduct> captor = ArgumentCaptor.forClass(MenuProduct.class);
        verify(menuProductRepository).save(captor.capture());
        assertThat(captor.getValue().isChefRecommended()).isTrue();
        assertThat(captor.getValue().getTagIds()).contains(8L);
    }

    @Test
    void listPublicProducts_whenChefRecommendedFilter_thenPassFilterToRepository() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .userId(7L)
                .active(true)
                .publicAccessEnabled(true)
                .build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of());
        when(menuProductRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        menuService.listPublicProducts(10L, 0, 20, true, null, null, null, null, null, null, null, null, null, null, null);

        verify(menuProductRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void createProduct_whenNutritionMissing_thenThrow() {
        Menu menu = Menu.builder().menuId(10L).userId(7L).build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));

        MenuDtos.MenuProductRequest request = MenuDtos.MenuProductRequest.builder()
                .name("Köfte")
                .subCategoryId(16L)
                .build();

        org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Besin ögesi bilgisi zorunludur"))
                .when(nutritionFactsService).validateForCreate(null);

        assertThatThrownBy(() -> menuService.createProduct(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Besin");
    }

    @Test
    void createProduct_whenSubCategoryIdMissing_thenThrow() {
        Menu menu = Menu.builder().menuId(10L).userId(7L).build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));

        MenuDtos.MenuProductRequest request = MenuDtos.MenuProductRequest.builder()
                .name("Köfte")
                .nutrition(sampleNutrition())
                .build();

        assertThatThrownBy(() -> menuService.createProduct(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("subCategoryId");
    }

    @Test
    void patchProductNutrition_whenOnlySalt_thenMergeAndPreserveName() {
        NutritionFacts existing = sampleNutrition();
        existing.setOtherNutrients(List.of(
                NutritionNutrientEntry.builder().name("Omega-3").value(new BigDecimal("0.5")).unit("g").build()
        ));
        MenuProduct product = MenuProduct.builder()
                .productId(5L)
                .menuId(10L)
                .name("Köfte")
                .price(new BigDecimal("180"))
                .currency("TRY")
                .subCategoryId(16L)
                .sortOrder(0)
                .available(true)
                .nutrition(existing)
                .build();
        Menu menu = Menu.builder().menuId(10L).userId(7L).build();
        NutritionFacts patch = NutritionFacts.builder().salt(new BigDecimal("1.4")).build();
        NutritionFacts merged = sampleNutrition();
        merged.setSalt(new BigDecimal("1.4"));
        merged.setOtherNutrients(existing.getOtherNutrients());

        when(menuProductRepository.findByProductIdAndDeletedFalse(5L)).thenReturn(Optional.of(product));
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(nutritionFactsService.merge(existing, patch)).thenReturn(merged);
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of(
                16L, SubCategory.builder().id(16L).mainCategoryId(5L).slug("et_yemekleri").name("Et").sortOrder(1).build()
        ));
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of());

        MenuDtos.MenuProductResponse response = menuService.patchProductNutrition(5L, patch);

        assertThat(response.getName()).isEqualTo("Köfte");
        assertThat(response.getPrice()).isEqualByComparingTo("180");
        assertThat(response.getNutrition().getSalt()).isEqualByComparingTo("1.4");
        assertThat(response.getNutrition().getOtherNutrients()).hasSize(1);
        verify(nutritionFactsService).merge(existing, patch);
    }

    private NutritionFacts sampleNutrition() {
        return NutritionFacts.builder()
                .basis(NutritionBasis.PER_100G)
                .energyKj(new BigDecimal("850"))
                .energyKcal(new BigDecimal("203"))
                .fat(new BigDecimal("10.5"))
                .carbohydrate(new BigDecimal("25"))
                .fibre(new BigDecimal("2.1"))
                .protein(new BigDecimal("8"))
                .salt(new BigDecimal("1.2"))
                .build();
    }

    @Test
    void getPublicMenuByQrId_whenPublicAccessDisabled_thenThrowForbidden() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .qrId(2L)
                .userId(7L)
                .themeId("soft")
                .businessName("Kafe")
                .active(true)
                .publicAccessEnabled(false)
                .build();
        when(menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(2L)).thenReturn(Optional.of(menu));
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));

        assertThatThrownBy(() -> menuService.getPublicMenuByQrId(2L))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(ex -> {
                    ForbiddenException forbidden = (ForbiddenException) ex;
                    assertThat(forbidden.getCode()).isEqualTo(ForbiddenException.MENU_OWNER_PACKAGE_INACTIVE);
                });
        verify(menuPublicAccessService).syncForUser(7L);
    }

    @Test
    void getPublicMenuByQrId_whenPublicAccessStale_thenResyncAndReturn() {
        Menu disabled = Menu.builder()
                .menuId(10L)
                .qrId(2L)
                .userId(7L)
                .themeId("soft")
                .businessName("Kafe")
                .active(true)
                .publicAccessEnabled(false)
                .publicAccessDisabledReason("PACKAGE_INACTIVE")
                .build();
        Menu enabled = Menu.builder()
                .menuId(10L)
                .qrId(2L)
                .userId(7L)
                .themeId("soft")
                .businessName("Kafe")
                .active(true)
                .publicAccessEnabled(true)
                .build();
        when(menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(2L)).thenReturn(Optional.of(disabled));
        when(menuRepository.findById(10L)).thenReturn(Optional.of(enabled));
        when(appProperties.getUrl()).thenReturn("https://example.com");
        when(menuProductRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(menuTaxonomyService.listTaxonomyPage(0, 6, null)).thenReturn(
                TaxonomyDtos.TaxonomyPageResponse.builder()
                        .content(List.of())
                        .page(0)
                        .size(6)
                        .totalElements(0)
                        .totalPages(0)
                        .hasNext(false)
                        .build()
        );
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of());

        MenuDtos.PublicMenuResponse response = menuService.getPublicMenuByQrId(2L);

        verify(menuPublicAccessService).syncForUser(7L);
        assertThat(response.getMenu().getBusinessName()).isEqualTo("Kafe");
    }

    @Test
    void getPublicMenuByQrId_whenPublicAccessEnabled_thenReturnPublicMenu() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .qrId(2L)
                .userId(7L)
                .themeId("soft")
                .businessName("Kafe")
                .active(true)
                .publicAccessEnabled(true)
                .build();
        when(menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(2L)).thenReturn(Optional.of(menu));
        when(appProperties.getUrl()).thenReturn("https://example.com");
        when(menuProductRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(menuTaxonomyService.listTaxonomyPage(0, 6, null)).thenReturn(
                TaxonomyDtos.TaxonomyPageResponse.builder()
                        .content(List.of())
                        .page(0)
                        .size(6)
                        .totalElements(0)
                        .totalPages(0)
                        .hasNext(false)
                        .build()
        );
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of());

        MenuDtos.PublicMenuResponse response = menuService.getPublicMenuByQrId(2L);

        assertThat(response.getThemeId()).isEqualTo("soft");
        assertThat(response.getMenu().getBusinessName()).isEqualTo("Kafe");
        assertThat(response.getProducts()).isEmpty();
        assertThat(response.getProductPage()).isZero();
        assertThat(response.getProductSize()).isEqualTo(20);
        assertThat(response.getProductTotalElements()).isZero();
        assertThat(response.isProductHasNext()).isFalse();
    }

    @Test
    void listProducts_whenMoreThanPageSize_thenReturnHasNext() {
        Menu menu = Menu.builder().menuId(10L).userId(7L).active(true).build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of());

        List<MenuProduct> pageContent = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            pageContent.add(MenuProduct.builder()
                    .productId((long) i)
                    .menuId(10L)
                    .name("Urun " + i)
                    .currency("TRY")
                    .subCategoryId(1L)
                    .sortOrder(i)
                    .available(true)
                    .build());
        }
        Pageable pageable = PageRequest.of(0, 20);
        when(menuProductRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(pageContent, pageable, 25));

        MenuDtos.MenuProductPageResponse response = menuService.listProducts(
                10L, 0, 20, null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertThat(response.getContent()).hasSize(20);
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(25);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.isHasNext()).isTrue();
    }

    @Test
    void listPublicProducts_whenUnavailablePresent_thenOnlyAvailableReturned() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .userId(7L)
                .active(true)
                .publicAccessEnabled(true)
                .build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of());

        MenuProduct available = MenuProduct.builder()
                .productId(1L)
                .menuId(10L)
                .name("Cay")
                .currency("TRY")
                .subCategoryId(1L)
                .sortOrder(0)
                .available(true)
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(menuProductRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(available), pageable, 1));

        MenuDtos.MenuProductPageResponse response = menuService.listPublicProducts(
                10L, 0, 20, null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getName()).isEqualTo("Cay");
        assertThat(response.isHasNext()).isFalse();
        verify(menuProductRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getPublicMenuByQrId_whenManyProducts_thenReturnFirstPageOnly() {
        Menu menu = Menu.builder()
                .menuId(10L)
                .qrId(2L)
                .userId(7L)
                .themeId("soft")
                .businessName("Kafe")
                .active(true)
                .publicAccessEnabled(true)
                .build();
        when(menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(2L)).thenReturn(Optional.of(menu));
        when(appProperties.getUrl()).thenReturn("https://example.com");
        when(menuTaxonomyService.listTaxonomyPage(0, 6, null)).thenReturn(
                TaxonomyDtos.TaxonomyPageResponse.builder()
                        .content(List.of())
                        .page(0)
                        .size(6)
                        .totalElements(0)
                        .totalPages(0)
                        .hasNext(false)
                        .build()
        );
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of());

        List<MenuProduct> firstPage = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            firstPage.add(MenuProduct.builder()
                    .productId((long) i)
                    .menuId(10L)
                    .name("Urun " + i)
                    .currency("TRY")
                    .subCategoryId(1L)
                    .sortOrder(i)
                    .available(true)
                    .build());
        }
        when(menuProductRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(firstPage, PageRequest.of(0, 20), 45));

        MenuDtos.PublicMenuResponse response = menuService.getPublicMenuByQrId(2L);

        assertThat(response.getProducts()).hasSize(20);
        assertThat(response.getProductTotalElements()).isEqualTo(45);
        assertThat(response.isProductHasNext()).isTrue();
        assertThat(response.getProductPage()).isZero();
        assertThat(response.getProductSize()).isEqualTo(20);
    }

    @Test
    void createProduct_whenAllergenIdsProvided_thenHydrateAllergens() {
        Menu menu = Menu.builder().menuId(10L).userId(7L).build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        when(menuTaxonomyService.requireSubCategory(16L))
                .thenReturn(SubCategory.builder().id(16L).mainCategoryId(5L).slug("et_yemekleri").name("Et").sortOrder(1).build());
        when(menuTaxonomyService.findTagBySlug(any())).thenReturn(Optional.empty());
        when(menuTaxonomyService.requireTags(any())).thenReturn(List.of());
        when(menuTaxonomyService.requireAllergens(any())).thenReturn(List.of(
                MenuAllergen.builder().id(7L).slug("sut").name("Süt").sortOrder(7).build()
        ));
        doNothing().when(nutritionFactsService).validateForCreate(any());
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(10L))
                .thenReturn(List.of());
        when(menuProductRepository.save(any(MenuProduct.class))).thenAnswer(invocation -> {
            MenuProduct saved = invocation.getArgument(0);
            saved.setProductId(66L);
            return saved;
        });
        when(menuTaxonomyService.loadSubCategoryMap()).thenReturn(Map.of(
                16L, SubCategory.builder().id(16L).mainCategoryId(5L).slug("et_yemekleri").name("Et").sortOrder(1).build()
        ));
        when(menuTaxonomyService.loadMainCategoryMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadTagMap()).thenReturn(Map.of());
        when(menuTaxonomyService.loadAllergenMap()).thenReturn(Map.of(
                7L, MenuAllergen.builder().id(7L).slug("sut").name("Süt").sortOrder(7).build()
        ));

        MenuDtos.MenuProductResponse response = menuService.createProduct(10L, MenuDtos.MenuProductRequest.builder()
                .name("Sütlaç")
                .subCategoryId(16L)
                .allergenIds(List.of(7L))
                .nutrition(sampleNutrition())
                .build());

        assertThat(response.getAllergens()).hasSize(1);
        assertThat(response.getAllergens().getFirst().getSlug()).isEqualTo("sut");
        ArgumentCaptor<MenuProduct> captor = ArgumentCaptor.forClass(MenuProduct.class);
        verify(menuProductRepository).save(captor.capture());
        assertThat(captor.getValue().getAllergenIds()).containsExactlyInAnyOrder(7L);
    }

    @Test
    void createProduct_whenInvalidAllergenId_thenBadRequest() {
        Menu menu = Menu.builder().menuId(10L).userId(7L).build();
        when(menuRepository.findById(10L)).thenReturn(Optional.of(menu));
        when(securityUtils.getCurrentUserId()).thenReturn(7L);
        when(menuTaxonomyService.requireSubCategory(16L))
                .thenReturn(SubCategory.builder().id(16L).mainCategoryId(5L).slug("et_yemekleri").name("Et").sortOrder(1).build());
        when(menuTaxonomyService.findTagBySlug(any())).thenReturn(Optional.empty());
        when(menuTaxonomyService.requireTags(any())).thenReturn(List.of());
        when(menuTaxonomyService.requireAllergens(any()))
                .thenThrow(new BadRequestException("Gecersiz allergen id"));
        doNothing().when(nutritionFactsService).validateForCreate(any());

        assertThatThrownBy(() -> menuService.createProduct(10L, MenuDtos.MenuProductRequest.builder()
                .name("Sütlaç")
                .subCategoryId(16L)
                .allergenIds(List.of(99L))
                .nutrition(sampleNutrition())
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("allergen");
    }
}
