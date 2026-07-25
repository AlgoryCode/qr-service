package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.UrlMode;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.repository.MenuCategoryRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.ael.algoryqrservice.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuService {

    public static final int DEFAULT_PRODUCT_PAGE_SIZE = 20;
    public static final int MAX_PRODUCT_PAGE_SIZE = 50;

    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuCategoryService menuCategoryService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final NutritionFactsService nutritionFactsService;
    private final QrRepository qrRepository;
    private final QrGenerationService qrGenerationService;
    private final AppProperties appProperties;
    private final SecurityUtils securityUtils;

    @Transactional
    public Menu createMenuForQr(Qr qr, QrRequest request) {
        Map<String, Object> details = request.getDetails();
        UrlMode urlMode = UrlMode.from(stringValue(details.get("urlMode")));
        String themeId = requireNonBlank(stringValue(details.get("themeId")), "themeId zorunludur");
        String businessName = requireNonBlank(stringValue(details.get("businessName")), "businessName zorunludur");
        String publicSlug = resolveSlug(urlMode, stringValue(details.get("publicSlug")), null);

        Menu menu = Menu.builder()
                .qrId(qr.getQrId())
                .userId(qr.getUserId())
                .themeId(themeId)
                .businessName(businessName)
                .slogan(trimToNull(stringValue(details.get("slogan"))))
                .phone(stringValue(details.get("phone")))
                .email(stringValue(details.get("email")))
                .address(stringValue(details.get("address")))
                .publicSlug(publicSlug)
                .urlMode(urlMode)
                .active(true)
                .build();

        menu = menuRepository.save(menu);
        createProductsFromDetails(menu, details.get("products"));
        menuPublicAccessService.syncForUser(menu.getUserId());
        return menu;
    }

    private void createProductsFromDetails(Menu menu, Object productsRaw) {
        if (!(productsRaw instanceof List<?> products) || products.isEmpty()) {
            return;
        }
        int index = 0;
        for (Object item : products) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = stringValue(map.get("name"));
            if (name == null || name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürün adı zorunludur");
            }
            Long categoryId = longValue(map.get("categoryId"));
            String categoryName = trimToNull(stringValue(map.get("category")));
            if (categoryId != null) {
                menuCategoryService.requireCategoryForMenu(menu.getMenuId(), categoryId);
            } else if (categoryName != null) {
                categoryId = resolveOrCreateRootCategory(menu.getMenuId(), categoryName);
            }
            var nutrition = nutritionFactsService.parseFromRaw(map.get("nutrition"));
            nutritionFactsService.validateForCreate(nutrition);
            MenuProduct product = MenuProduct.builder()
                    .menuId(menu.getMenuId())
                    .name(name.trim())
                    .description(trimToNull(stringValue(map.get("description"))))
                    .price(decimalValue(map.get("price")))
                    .currency(currencyValue(map.get("currency")))
                    .category(categoryName)
                    .categoryId(categoryId)
                    .sortOrder(integerValue(map.get("sortOrder"), index))
                    .imageUrl(trimToNull(stringValue(map.get("imageUrl"))))
                    .available(booleanValue(map.get("available"), true))
                    .nutrition(nutrition)
                    .build();
            menuProductRepository.save(product);
            index++;
        }
    }

    private java.math.BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz ürün fiyatı");
        }
    }

    private String currencyValue(Object value) {
        String currency = trimToNull(stringValue(value));
        return currency == null ? "TRY" : currency;
    }

    private int integerValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    public String buildPublicUrl(Menu menu) {
        String base = trimTrailingSlash(appProperties.getUrl());
        if (menu.getUrlMode() == UrlMode.SLUG) {
            return base + "/menu/" + menu.getPublicSlug();
        }
        return base + "/menu/" + menu.getQrId();
    }

    public String buildPublicUrlForMode(UrlMode urlMode, Long qrId, String publicSlug) {
        String base = trimTrailingSlash(appProperties.getUrl());
        if (urlMode == UrlMode.SLUG) {
            return base + "/menu/" + SlugUtils.normalize(publicSlug);
        }
        return base + "/menu/" + qrId;
    }

    @Transactional(readOnly = true)
    public MenuDtos.PublicMenuResponse getPublicMenuByQrId(Long qrId) {
        Menu menu = menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(qrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        return buildPublicResponse(menu);
    }

    @Transactional(readOnly = true)
    public MenuDtos.PublicMenuResponse getPublicMenuBySlug(String slug) {
        String normalized = SlugUtils.normalize(slug);
        Menu menu = menuRepository.findByPublicSlugIgnoreCaseAndActiveTrueAndDeletedFalse(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        return buildPublicResponse(menu);
    }

    @Transactional(readOnly = true)
    public MenuDtos.SlugAvailabilityResponse checkSlugAvailability(String slug, Long excludeMenuId) {
        String normalized = SlugUtils.normalize(slug);
        if (!SlugUtils.isValid(normalized)) {
            return MenuDtos.SlugAvailabilityResponse.builder()
                    .slug(normalized)
                    .available(false)
                    .build();
        }
        boolean available = excludeMenuId == null
                ? !menuRepository.existsByPublicSlugIgnoreCaseAndDeletedFalse(normalized)
                : !menuRepository.existsByPublicSlugIgnoreCaseAndDeletedFalseAndMenuIdNot(normalized, excludeMenuId);
        return MenuDtos.SlugAvailabilityResponse.builder()
                .slug(normalized)
                .available(available)
                .build();
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuProductPageResponse listProducts(Long menuId, int page, int size) {
        ensureOwnedMenu(menuId);
        Pageable pageable = productPageable(page, size);
        Page<MenuProduct> productPage = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId, pageable);
        return toProductPageResponse(productPage, menuId);
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuProductPageResponse listPublicProducts(Long menuId, int page, int size) {
        Menu menu = ensureMenuExists(menuId);
        return listAvailableProductsPage(menu, page, size);
    }

    @Transactional
    public MenuDtos.MenuProductResponse createProduct(Long menuId, MenuDtos.MenuProductRequest request) {
        Menu menu = ensureOwnedMenu(menuId);
        validateProductRequest(request);
        nutritionFactsService.validateForCreate(request.getNutrition());
        Long categoryId = resolveCategoryId(menu.getMenuId(), request);

        MenuProduct product = MenuProduct.builder()
                .menuId(menu.getMenuId())
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .price(request.getPrice())
                .currency(request.getCurrency() != null && !request.getCurrency().isBlank() ? request.getCurrency().trim() : "TRY")
                .category(resolveCategoryLabel(menu.getMenuId(), categoryId, request.getCategory()))
                .categoryId(categoryId)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSortOrder(menuId))
                .imageUrl(trimToNull(request.getImageUrl()))
                .available(request.getAvailable() == null || request.getAvailable())
                .nutrition(request.getNutrition())
                .build();

        return toProductResponse(menuProductRepository.save(product));
    }

    @Transactional
    public MenuDtos.MenuProductResponse updateProduct(Long productId, MenuDtos.MenuProductRequest request) {
        MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        ensureOwnedMenu(product.getMenuId());
        validateProductRequest(request);
        Long categoryId = resolveCategoryId(product.getMenuId(), request);

        product.setName(request.getName().trim());
        product.setDescription(trimToNull(request.getDescription()));
        product.setPrice(request.getPrice());
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            product.setCurrency(request.getCurrency().trim());
        }
        product.setCategory(resolveCategoryLabel(product.getMenuId(), categoryId, request.getCategory()));
        product.setCategoryId(categoryId);
        if (request.getSortOrder() != null) {
            product.setSortOrder(request.getSortOrder());
        }
        product.setImageUrl(trimToNull(request.getImageUrl()));
        if (request.getAvailable() != null) {
            product.setAvailable(request.getAvailable());
        }
        if (request.getNutrition() != null) {
            product.setNutrition(nutritionFactsService.merge(product.getNutrition(), request.getNutrition()));
        }

        return toProductResponse(menuProductRepository.save(product));
    }

    @Transactional
    public MenuDtos.MenuProductResponse patchProductNutrition(Long productId, NutritionFacts patch) {
        MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        ensureOwnedMenu(product.getMenuId());
        if (patch == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Besin ögesi bilgisi zorunludur");
        }
        product.setNutrition(nutritionFactsService.merge(product.getNutrition(), patch));
        return toProductResponse(menuProductRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long productId) {
        MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        ensureOwnedMenu(product.getMenuId());
        product.setDeleted(true);
        menuProductRepository.save(product);
    }

    @Transactional
    public MenuDtos.MenuProfileResponse updateMenu(Long menuId, MenuDtos.MenuUpdateRequest request) throws Exception {
        Menu menu = ensureOwnedMenu(menuId);
        Qr qr = qrRepository.findById(menu.getQrId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR bulunamadı"));

        if (request.getThemeId() != null && !request.getThemeId().isBlank()) {
            menu.setThemeId(request.getThemeId().trim());
        }
        if (request.getBusinessName() != null && !request.getBusinessName().isBlank()) {
            menu.setBusinessName(request.getBusinessName().trim());
        }
        if (request.getSlogan() != null) {
            menu.setSlogan(trimToNull(request.getSlogan()));
        }
        if (request.getPhone() != null) menu.setPhone(trimToNull(request.getPhone()));
        if (request.getEmail() != null) menu.setEmail(trimToNull(request.getEmail()));
        if (request.getAddress() != null) menu.setAddress(trimToNull(request.getAddress()));
        if (request.getActive() != null) menu.setActive(request.getActive());

        UrlMode nextMode = request.getUrlMode() != null ? UrlMode.from(request.getUrlMode()) : menu.getUrlMode();
        String nextSlug = menu.getPublicSlug();
        if (nextMode == UrlMode.SLUG) {
            String candidate = request.getPublicSlug() != null ? request.getPublicSlug() : menu.getPublicSlug();
            nextSlug = resolveSlug(nextMode, candidate, menu.getMenuId());
        } else {
            nextSlug = null;
        }

        menu.setUrlMode(nextMode);
        menu.setPublicSlug(nextSlug);
        menuRepository.save(menu);

        String publicUrl = buildPublicUrl(menu);
        qrGenerationService.updateQrContent(qr, publicUrl);

        return toMenuProfile(menu, publicUrl, null);
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuProfileResponse getMenuProfile(Long menuId) {
        Menu menu = ensureOwnedMenu(menuId);
        return toMenuProfile(menu, buildPublicUrl(menu), null);
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuProfileResponse getMenuProfileByQrId(Long qrId) {
        List<Object[]> rows = menuRepository.findActiveMenuWithQrByQrId(qrId);
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.getFirst();
        Menu menu = (Menu) row[0];
        Qr qr = (Qr) row[1];
        requireOwnership(menu);
        return toMenuProfile(
                menu,
                buildPublicUrl(menu),
                MenuDtos.QrBrief.builder()
                        .id(qr.getQrId())
                        .name(qr.getQrName())
                        .imgSrc(qr.getImgSrc())
                        .build()
        );
    }

    @Transactional(readOnly = true)
    public List<MenuDtos.ActiveMenuSummary> listActiveMenusForCurrentUser() {
        Long userId = securityUtils.getCurrentUserId();
        return menuRepository.findActiveMenusWithQrByUserId(userId).stream()
                .map(row -> {
                    Menu menu = (Menu) row[0];
                    Qr qr = (Qr) row[1];
                    return MenuDtos.ActiveMenuSummary.builder()
                            .menuId(menu.getMenuId())
                            .qrId(menu.getQrId())
                            .businessName(menu.getBusinessName())
                            .themeId(menu.getThemeId())
                            .publicUrl(buildPublicUrl(menu))
                            .active(menu.isActive())
                            .qr(MenuDtos.QrNameBrief.builder()
                                    .id(qr.getQrId())
                                    .name(qr.getQrName())
                                    .build())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuProductsByQrResponse listProductsByQrId(Long qrId) {
        Long userId = securityUtils.getCurrentUserId();
        List<Object[]> rows = menuProductRepository.findMenuWithProductsByQrIdAndUserId(qrId, userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı");
        }
        Menu menu = (Menu) rows.getFirst()[0];
        Map<Long, MenuCategory> categoryMap = menuCategoryService.loadCategoryMap(menu.getMenuId());
        List<MenuDtos.MenuProductResponse> products = rows.stream()
                .map(row -> (MenuProduct) row[1])
                .filter(product -> product != null)
                .map(product -> toProductResponse(product, categoryMap))
                .toList();
        return MenuDtos.MenuProductsByQrResponse.builder()
                .menuId(menu.getMenuId())
                .qrId(menu.getQrId())
                .businessName(menu.getBusinessName())
                .content(products)
                .page(0)
                .size(products.size())
                .totalElements(products.size())
                .totalPages(1)
                .hasNext(false)
                .build();
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuCategoriesByQrResponse listCategoriesByQrId(Long qrId) {
        Long userId = securityUtils.getCurrentUserId();
        List<Object[]> rows = menuCategoryRepository.findMenuWithCategoriesByQrIdAndUserId(qrId, userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı");
        }
        Menu menu = (Menu) rows.getFirst()[0];
        List<MenuCategory> categories = rows.stream()
                .map(row -> (MenuCategory) row[1])
                .filter(category -> category != null)
                .toList();
        return MenuDtos.MenuCategoriesByQrResponse.builder()
                .menuId(menu.getMenuId())
                .qrId(menu.getQrId())
                .businessName(menu.getBusinessName())
                .categories(menuCategoryService.buildTreeFromList(categories))
                .build();
    }

    @Transactional(readOnly = true)
    public Menu findByQrId(Long qrId) {
        Menu menu = menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(qrId).orElse(null);
        if (menu != null) {
            requireOwnership(menu);
        }
        return menu;
    }

    private Menu ensureMenuExists(Long menuId) {
        return menuRepository.findById(menuId)
                .filter(menu -> !menu.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
    }

    private Menu ensureOwnedMenu(Long menuId) {
        Menu menu = ensureMenuExists(menuId);
        requireOwnership(menu);
        return menu;
    }

    private void requireOwnership(Menu menu) {
        Long currentUserId = securityUtils.getCurrentUserId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
    }

    private int nextSortOrder(Long menuId) {
        return menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId).size();
    }

    private void ensurePublicAccess(Menu menu) {
        if (!menu.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü yayında değil");
        }
        if (!menu.isPublicAccessEnabled()) {
            throw new ForbiddenException(
                    ForbiddenException.MENU_OWNER_PACKAGE_INACTIVE,
                    "Lütfen restoran sahibiyle iletişime geçiniz."
            );
        }
    }

    private MenuDtos.MenuProductPageResponse listAvailableProductsPage(Menu menu, int page, int size) {
        ensurePublicAccess(menu);
        Pageable pageable = productPageable(page, size);
        Page<MenuProduct> productPage = menuProductRepository
                .findByMenuIdAndDeletedFalseAndAvailableTrueOrderBySortOrderAscProductIdAsc(
                        menu.getMenuId(),
                        pageable
                );
        return toProductPageResponse(productPage, menu.getMenuId());
    }

    private MenuDtos.PublicMenuResponse buildPublicResponse(Menu menu) {
        MenuDtos.MenuProductPageResponse productPage = listAvailableProductsPage(
                menu,
                0,
                DEFAULT_PRODUCT_PAGE_SIZE
        );
        List<MenuDtos.MenuCategoryResponse> categories = menuCategoryService.listPublicCategoryTree(menu.getMenuId());

        return MenuDtos.PublicMenuResponse.builder()
                .menu(toMenuProfile(menu, buildPublicUrl(menu), null))
                .products(productPage.getContent())
                .categories(categories)
                .themeId(menu.getThemeId())
                .productPage(productPage.getPage())
                .productSize(productPage.getSize())
                .productTotalElements(productPage.getTotalElements())
                .productHasNext(productPage.isHasNext())
                .build();
    }

    private Pageable productPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PRODUCT_PAGE_SIZE : Math.min(size, MAX_PRODUCT_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }

    private MenuDtos.MenuProductPageResponse toProductPageResponse(Page<MenuProduct> productPage, Long menuId) {
        Map<Long, MenuCategory> categoryMap = menuCategoryService.loadCategoryMap(menuId);
        List<MenuDtos.MenuProductResponse> content = productPage.getContent().stream()
                .map(product -> toProductResponse(product, categoryMap))
                .toList();
        return MenuDtos.MenuProductPageResponse.builder()
                .content(content)
                .page(productPage.getNumber())
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .hasNext(productPage.hasNext())
                .build();
    }

    private MenuDtos.MenuProductResponse toProductResponse(MenuProduct product) {
        return toProductResponse(product, menuCategoryService.loadCategoryMap(product.getMenuId()));
    }

    private MenuDtos.MenuProductResponse toProductResponse(MenuProduct product, Map<Long, MenuCategory> categoryMap) {
        Long categoryId = product.getCategoryId();
        String categoryName = menuCategoryService.resolveCategoryName(categoryId, categoryMap);
        String categoryPath = menuCategoryService.resolveCategoryPath(categoryId, categoryMap);
        String legacyCategory = categoryName != null ? categoryName : product.getCategory();

        return MenuDtos.MenuProductResponse.builder()
                .productId(product.getProductId())
                .menuId(product.getMenuId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .category(legacyCategory)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .categoryPath(categoryPath)
                .sortOrder(product.getSortOrder())
                .imageUrl(product.getImageUrl())
                .available(product.isAvailable())
                .nutrition(product.getNutrition())
                .build();
    }

    private Long resolveCategoryId(Long menuId, MenuDtos.MenuProductRequest request) {
        if (request.getCategoryId() != null) {
            menuCategoryService.requireCategoryForMenu(menuId, request.getCategoryId());
            return request.getCategoryId();
        }
        String categoryName = trimToNull(request.getCategory());
        if (categoryName != null) {
            return resolveOrCreateRootCategory(menuId, categoryName);
        }
        return null;
    }

    private String resolveCategoryLabel(Long menuId, Long categoryId, String fallbackCategory) {
        if (categoryId == null) {
            return trimToNull(fallbackCategory);
        }
        Map<Long, MenuCategory> categoryMap = menuCategoryService.loadCategoryMap(menuId);
        String categoryName = menuCategoryService.resolveCategoryName(categoryId, categoryMap);
        return categoryName != null ? categoryName : trimToNull(fallbackCategory);
    }

    private Long resolveOrCreateRootCategory(Long menuId, String categoryName) {
        return menuCategoryService.findOrCreateRootCategory(menuId, categoryName);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private MenuDtos.MenuProfileResponse toMenuProfile(
            Menu menu,
            String publicUrl,
            MenuDtos.QrBrief qrBrief
    ) {
        return MenuDtos.MenuProfileResponse.builder()
                .menuId(menu.getMenuId())
                .qrId(menu.getQrId())
                .userId(menu.getUserId())
                .themeId(menu.getThemeId())
                .businessName(menu.getBusinessName())
                .slogan(menu.getSlogan())
                .phone(menu.getPhone())
                .email(menu.getEmail())
                .address(menu.getAddress())
                .publicSlug(menu.getPublicSlug())
                .urlMode(menu.getUrlMode().name())
                .publicUrl(publicUrl)
                .active(menu.isActive())
                .qr(qrBrief)
                .build();
    }

    private void validateProductRequest(MenuDtos.MenuProductRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürün adı zorunludur");
        }
    }

    private String resolveSlug(UrlMode urlMode, String rawSlug, Long excludeMenuId) {
        if (urlMode != UrlMode.SLUG) {
            return null;
        }
        String normalized = SlugUtils.normalize(rawSlug);
        if (!SlugUtils.isValid(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz slug formatı");
        }
        boolean taken = excludeMenuId == null
                ? menuRepository.existsByPublicSlugIgnoreCaseAndDeletedFalse(normalized)
                : menuRepository.existsByPublicSlugIgnoreCaseAndDeletedFalseAndMenuIdNot(normalized, excludeMenuId);
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu slug zaten kullanılıyor");
        }
        return normalized;
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/$", "");
    }
}
