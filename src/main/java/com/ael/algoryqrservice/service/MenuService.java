package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.MainCategory;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuAllergen;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuTag;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuProductSpecifications;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MenuService {

    public static final int DEFAULT_PRODUCT_PAGE_SIZE = 20;
    public static final int MAX_PRODUCT_PAGE_SIZE = 50;
    public static final int DEFAULT_RECOMMENDATION_LIMIT = 6;
    public static final int MAX_RECOMMENDATION_LIMIT = 20;

    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuTaxonomyService menuTaxonomyService;
    private final MenuPublicAccessService menuPublicAccessService;
    private final NutritionFactsService nutritionFactsService;
    private final ServesPeopleSupport servesPeopleSupport;
    private final QrRepository qrRepository;
    private final QrGenerationService qrGenerationService;
    private final AppProperties appProperties;
    private final SecurityUtils securityUtils;
    private final ProductImageStorageService productImageStorageService;

    @Transactional(readOnly = true)
    public void requireOwnedMenu(Long menuId) {
        ensureOwnedMenu(menuId);
    }

    @Transactional
    public Menu createMenuForQr(Qr qr, QrRequest request) {
        Map<String, Object> details = request.getDetails();
        String themeId = requireNonBlank(stringValue(details.get("themeId")), "themeId zorunludur");
        String businessName = requireNonBlank(stringValue(details.get("businessName")), "businessName zorunludur");

        Menu menu = Menu.builder()
                .qrId(qr.getQrId())
                .userId(qr.getUserId())
                .themeId(themeId)
                .businessName(businessName)
                .slogan(trimToNull(stringValue(details.get("slogan"))))
                .phone(stringValue(details.get("phone")))
                .email(stringValue(details.get("email")))
                .address(stringValue(details.get("address")))
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
            Long subCategoryId = longValue(map.get("subCategoryId"));
            if (subCategoryId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subCategoryId zorunludur");
            }
            menuTaxonomyService.requireSubCategory(subCategoryId);
            Set<Long> tagIds = parseTagIds(map.get("tagIds"));
            Set<Long> allergenIds = parseTagIds(map.get("allergenIds"));
            Boolean chefFlag = map.get("chefRecommended") == null
                    ? null
                    : booleanValue(map.get("chefRecommended"), false);
            tagIds = applyChefRecommended(tagIds, chefFlag);
            menuTaxonomyService.requireTags(tagIds);
            menuTaxonomyService.requireAllergens(allergenIds);
            var nutrition = nutritionFactsService.parseFromRaw(map.get("nutrition"));
            nutritionFactsService.validateForCreate(nutrition);
            Integer servesMin = integerOrNull(map.get("servesPeopleMin"));
            Integer servesMax = integerOrNull(map.get("servesPeopleMax"));
            ServesPeopleSupport.Range serves = servesPeopleSupport.resolveFromSeed(servesMin, servesMax, name);
            MenuProduct product = MenuProduct.builder()
                    .menuId(menu.getMenuId())
                    .name(name.trim())
                    .description(trimToNull(stringValue(map.get("description"))))
                    .price(decimalValue(map.get("price")))
                    .currency(currencyValue(map.get("currency")))
                    .subCategoryId(subCategoryId)
                    .tagIds(tagIds)
                    .allergenIds(allergenIds)
                    .chefRecommended(resolveChefRecommended(tagIds, chefFlag))
                    .sortOrder(integerValue(map.get("sortOrder"), index))
                    .imageUrl(resolveProductImageUrl(trimToNull(stringValue(map.get("imageUrl")))))
                    .available(booleanValue(map.get("available"), true))
                    .servesPeopleMin(serves.min())
                    .servesPeopleMax(serves.max())
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
        return buildPublicUrlForQrId(menu.getQrId());
    }

    public String buildPublicUrlForQrId(Long qrId) {
        String base = trimTrailingSlash(appProperties.getUrl());
        return base + "/menu/" + qrId;
    }

    @Transactional(readOnly = true)
    public MenuDtos.PublicMenuResponse getPublicMenuByQrId(Long qrId) {
        Menu menu = menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(qrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        return buildPublicResponse(menu);
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuProductPageResponse listProducts(
            Long menuId,
            int page,
            int size,
            Boolean chefRecommended,
            String tagSlug,
            BigDecimal minRating,
            Long subCategoryId,
            Long mainCategoryId,
            List<Long> tagIds,
            String allergenSlug,
            List<Long> allergenIds,
            Integer servesPeople,
            Integer servesPeopleMin,
            Integer servesPeopleMax,
            String q
    ) {
        ensureOwnedMenu(menuId);
        return searchProductsPage(
                menuId,
                false,
                page,
                size,
                chefRecommended,
                tagSlug,
                minRating,
                subCategoryId,
                mainCategoryId,
                tagIds,
                allergenSlug,
                allergenIds,
                servesPeople,
                servesPeopleMin,
                servesPeopleMax,
                q
        );
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuProductPageResponse listPublicProducts(
            Long menuId,
            int page,
            int size,
            Boolean chefRecommended,
            String tagSlug,
            BigDecimal minRating,
            Long subCategoryId,
            Long mainCategoryId,
            List<Long> tagIds,
            String allergenSlug,
            List<Long> allergenIds,
            Integer servesPeople,
            Integer servesPeopleMin,
            Integer servesPeopleMax,
            String q
    ) {
        Menu menu = ensureMenuExists(menuId);
        ensurePublicAccess(menu);
        return searchProductsPage(
                menu.getMenuId(),
                true,
                page,
                size,
                chefRecommended,
                tagSlug,
                minRating,
                subCategoryId,
                mainCategoryId,
                tagIds,
                allergenSlug,
                allergenIds,
                servesPeople,
                servesPeopleMin,
                servesPeopleMax,
                q
        );
    }

    @Transactional(readOnly = true)
    public MenuDtos.ProductFacetsResponse listPublicProductFacets(
            Long menuId,
            Boolean chefRecommended,
            String tagSlug,
            BigDecimal minRating,
            Long subCategoryId,
            Long mainCategoryId,
            List<Long> tagIds,
            String allergenSlug,
            List<Long> allergenIds,
            Integer servesPeople,
            Integer servesPeopleMin,
            Integer servesPeopleMax,
            String q
    ) {
        Menu menu = ensureMenuExists(menuId);
        ensurePublicAccess(menu);
        Specification<MenuProduct> spec = buildSearchSpec(
                menu.getMenuId(),
                true,
                chefRecommended,
                tagSlug,
                minRating,
                subCategoryId,
                mainCategoryId,
                tagIds,
                allergenSlug,
                allergenIds,
                servesPeople,
                servesPeopleMin,
                servesPeopleMax,
                q
        );
        List<MenuProduct> products = menuProductRepository.findAll(spec);
        Map<Long, MenuTag> tagMap = menuTaxonomyService.loadTagMap();
        Map<Long, MenuAllergen> allergenMap = menuTaxonomyService.loadAllergenMap();
        Map<Long, Long> tagCounts = new HashMap<>();
        Map<Long, Long> allergenCounts = new HashMap<>();
        long bucket1 = 0;
        long bucket2 = 0;
        long bucket34 = 0;
        long bucket5 = 0;
        for (MenuProduct product : products) {
            if (product.getTagIds() != null) {
                for (Long tagId : product.getTagIds()) {
                    tagCounts.merge(tagId, 1L, Long::sum);
                }
            }
            if (product.getAllergenIds() != null) {
                for (Long allergenId : product.getAllergenIds()) {
                    allergenCounts.merge(allergenId, 1L, Long::sum);
                }
            }
            if (servesPeopleSupport.overlapsBucket(product.getServesPeopleMin(), product.getServesPeopleMax(), 1, 1)) {
                bucket1++;
            }
            if (servesPeopleSupport.overlapsBucket(product.getServesPeopleMin(), product.getServesPeopleMax(), 2, 2)) {
                bucket2++;
            }
            if (servesPeopleSupport.overlapsBucket(product.getServesPeopleMin(), product.getServesPeopleMax(), 3, 4)) {
                bucket34++;
            }
            if (servesPeopleSupport.overlapsBucket(product.getServesPeopleMin(), product.getServesPeopleMax(), 5, null)) {
                bucket5++;
            }
        }
        List<MenuDtos.TagFacetCount> tags = tagCounts.entrySet().stream()
                .map(entry -> {
                    MenuTag tag = tagMap.get(entry.getKey());
                    if (tag == null) {
                        return null;
                    }
                    return MenuDtos.TagFacetCount.builder()
                            .tagId(tag.getId())
                            .slug(tag.getSlug())
                            .name(tag.getName())
                            .count(entry.getValue())
                            .build();
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(MenuDtos.TagFacetCount::getCount).reversed())
                .toList();
        List<MenuDtos.AllergenFacetCount> allergens = allergenCounts.entrySet().stream()
                .map(entry -> {
                    MenuAllergen allergen = allergenMap.get(entry.getKey());
                    if (allergen == null) {
                        return null;
                    }
                    return MenuDtos.AllergenFacetCount.builder()
                            .allergenId(allergen.getId())
                            .slug(allergen.getSlug())
                            .name(allergen.getName())
                            .count(entry.getValue())
                            .build();
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(MenuDtos.AllergenFacetCount::getCount).reversed())
                .toList();
        List<MenuDtos.ServesBucketFacet> buckets = List.of(
                MenuDtos.ServesBucketFacet.builder().key("1").label("1 kişilik").count(bucket1).build(),
                MenuDtos.ServesBucketFacet.builder().key("2").label("2 kişilik").count(bucket2).build(),
                MenuDtos.ServesBucketFacet.builder().key("3-4").label("3–4 kişilik").count(bucket34).build(),
                MenuDtos.ServesBucketFacet.builder().key("5+").label("5+ kişilik").count(bucket5).build()
        );
        return MenuDtos.ProductFacetsResponse.builder()
                .totalMatching(products.size())
                .tags(tags)
                .allergens(allergens)
                .servesBuckets(buckets)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuProductResponse> listPublicRecommendations(Long menuId, Long productId, int limit) {
        Menu menu = ensureMenuExists(menuId);
        ensurePublicAccess(menu);
        MenuProduct target = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                .filter(product -> product.getMenuId().equals(menuId) && product.isAvailable())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        int safeLimit = limit <= 0 ? DEFAULT_RECOMMENDATION_LIMIT : Math.min(limit, MAX_RECOMMENDATION_LIMIT);
        Map<Long, SubCategory> subMap = menuTaxonomyService.loadSubCategoryMap();
        Map<Long, MainCategory> mainMap = menuTaxonomyService.loadMainCategoryMap();
        Map<Long, MenuTag> tagMap = menuTaxonomyService.loadTagMap();
        Map<Long, MenuAllergen> allergenMap = menuTaxonomyService.loadAllergenMap();
        SubCategory targetSub = subMap.get(target.getSubCategoryId());
        Long targetMainId = targetSub == null ? null : targetSub.getMainCategoryId();
        double targetMid = servesPeopleSupport.midpoint(target.getServesPeopleMin(), target.getServesPeopleMax());
        Set<Long> targetTags = target.getTagIds() == null ? Set.of() : target.getTagIds();
        Long popularTagId = menuTaxonomyService.findTagBySlug("populer").map(MenuTag::getId).orElse(null);
        Long chefTagId = menuTaxonomyService.findTagBySlug(MenuTaxonomyService.CHEF_RECOMMENDED_TAG_SLUG)
                .map(MenuTag::getId).orElse(null);
        Long newTagId = menuTaxonomyService.findTagBySlug("yeni").map(MenuTag::getId).orElse(null);

        List<MenuProduct> candidates = menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId)
                .stream()
                .filter(MenuProduct::isAvailable)
                .filter(product -> !product.getProductId().equals(productId))
                .toList();

        return candidates.stream()
                .sorted(Comparator.comparingDouble((MenuProduct candidate) -> {
                    double score = 0;
                    if (candidate.getSubCategoryId().equals(target.getSubCategoryId())) {
                        score += 40;
                    }
                    SubCategory candidateSub = subMap.get(candidate.getSubCategoryId());
                    if (targetMainId != null && candidateSub != null
                            && targetMainId.equals(candidateSub.getMainCategoryId())) {
                        score += 20;
                    }
                    double mid = servesPeopleSupport.midpoint(
                            candidate.getServesPeopleMin(),
                            candidate.getServesPeopleMax()
                    );
                    score += Math.max(0, 15 - Math.abs(targetMid - mid) * 5);
                    Set<Long> candidateTags = candidate.getTagIds() == null ? Set.of() : candidate.getTagIds();
                    long shared = targetTags.stream().filter(candidateTags::contains).count();
                    score += shared * 8;
                    if (popularTagId != null && candidateTags.contains(popularTagId)) {
                        score += 6;
                    }
                    if (chefTagId != null && candidateTags.contains(chefTagId)) {
                        score += 5;
                    }
                    if (newTagId != null && candidateTags.contains(newTagId)) {
                        score += 3;
                    }
                    if (candidate.isChefRecommended()) {
                        score += 4;
                    }
                    return -score;
                }).thenComparing(MenuProduct::getSortOrder).thenComparing(MenuProduct::getProductId))
                .limit(safeLimit)
                .map(product -> toProductResponse(product, subMap, mainMap, tagMap, allergenMap))
                .toList();
    }

    @Transactional
    public MenuDtos.MenuProductResponse createProduct(Long menuId, MenuDtos.MenuProductRequest request) {
        Menu menu = ensureOwnedMenu(menuId);
        validateProductRequest(request);
        nutritionFactsService.validateForCreate(request.getNutrition());
        SubCategory subCategory = menuTaxonomyService.requireSubCategory(request.getSubCategoryId());
        Set<Long> tagIds = normalizeTagIds(request.getTagIds());
        tagIds = applyChefRecommended(tagIds, request.getChefRecommended());
        menuTaxonomyService.requireTags(tagIds);
        Set<Long> allergenIds = normalizeTagIds(request.getAllergenIds());
        menuTaxonomyService.requireAllergens(allergenIds);
        ServesPeopleSupport.Range serves = servesPeopleSupport.normalize(
                request.getServesPeopleMin(),
                request.getServesPeopleMax()
        );
        String imageUrl = resolveProductImageUrl(request.getImageUrl());

        MenuProduct product = MenuProduct.builder()
                .menuId(menu.getMenuId())
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .price(request.getPrice())
                .currency(request.getCurrency() != null && !request.getCurrency().isBlank() ? request.getCurrency().trim() : "TRY")
                .subCategoryId(subCategory.getId())
                .tagIds(tagIds)
                .allergenIds(allergenIds)
                .chefRecommended(resolveChefRecommended(tagIds, request.getChefRecommended()))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSortOrder(menuId))
                .imageUrl(imageUrl)
                .available(request.getAvailable() == null || request.getAvailable())
                .servesPeopleMin(serves.min())
                .servesPeopleMax(serves.max())
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
        SubCategory subCategory = menuTaxonomyService.requireSubCategory(request.getSubCategoryId());
        Set<Long> tagIds = normalizeTagIds(request.getTagIds());
        tagIds = applyChefRecommended(tagIds, request.getChefRecommended());
        menuTaxonomyService.requireTags(tagIds);
        Set<Long> allergenIds = normalizeTagIds(request.getAllergenIds());
        menuTaxonomyService.requireAllergens(allergenIds);
        ServesPeopleSupport.Range serves = servesPeopleSupport.normalize(
                request.getServesPeopleMin(),
                request.getServesPeopleMax()
        );

        product.setName(request.getName().trim());
        product.setDescription(trimToNull(request.getDescription()));
        product.setPrice(request.getPrice());
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            product.setCurrency(request.getCurrency().trim());
        }
        product.setSubCategoryId(subCategory.getId());
        product.setTagIds(tagIds);
        product.setAllergenIds(allergenIds);
        product.setChefRecommended(resolveChefRecommended(tagIds, request.getChefRecommended()));
        if (request.getSortOrder() != null) {
            product.setSortOrder(request.getSortOrder());
        }
        String newImageUrl = resolveProductImageUrl(request.getImageUrl());
        if (!Objects.equals(product.getImageUrl(), newImageUrl)) {
            productImageStorageService.deleteQuietly(productImageStorageService.extractObjectKey(product.getImageUrl()));
            product.setImageUrl(newImageUrl);
        }
        if (request.getAvailable() != null) {
            product.setAvailable(request.getAvailable());
        }
        product.setServesPeopleMin(serves.min());
        product.setServesPeopleMax(serves.max());
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
        productImageStorageService.deleteQuietly(productImageStorageService.extractObjectKey(product.getImageUrl()));
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
        List<MenuDtos.MenuProductResponse> products = rows.stream()
                .map(row -> (MenuProduct) row[1])
                .filter(product -> product != null)
                .map(this::toProductResponse)
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
        Menu menu = menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(qrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        requireOwnership(menu);
        return MenuDtos.MenuCategoriesByQrResponse.builder()
                .menuId(menu.getMenuId())
                .qrId(menu.getQrId())
                .businessName(menu.getBusinessName())
                .categories(menuTaxonomyService.listTaxonomy())
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
        return searchProductsPage(
                menu.getMenuId(), true, page, size,
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    private MenuDtos.MenuProductPageResponse searchProductsPage(
            Long menuId,
            boolean availableOnly,
            int page,
            int size,
            Boolean chefRecommended,
            String tagSlug,
            BigDecimal minRating,
            Long subCategoryId,
            Long mainCategoryId,
            List<Long> tagIds,
            String allergenSlug,
            List<Long> allergenIds,
            Integer servesPeople,
            Integer servesPeopleMin,
            Integer servesPeopleMax,
            String q
    ) {
        Specification<MenuProduct> spec = buildSearchSpec(
                menuId,
                availableOnly,
                chefRecommended,
                tagSlug,
                minRating,
                subCategoryId,
                mainCategoryId,
                tagIds,
                allergenSlug,
                allergenIds,
                servesPeople,
                servesPeopleMin,
                servesPeopleMax,
                q
        );
        Pageable pageable = productPageable(page, size);
        Page<MenuProduct> productPage = menuProductRepository.findAll(spec, pageable);
        return toProductPageResponse(productPage, menuId);
    }

    private Specification<MenuProduct> buildSearchSpec(
            Long menuId,
            boolean availableOnly,
            Boolean chefRecommended,
            String tagSlug,
            BigDecimal minRating,
            Long subCategoryId,
            Long mainCategoryId,
            List<Long> tagIds,
            String allergenSlug,
            List<Long> allergenIds,
            Integer servesPeople,
            Integer servesPeopleMin,
            Integer servesPeopleMax,
            String q
    ) {
        Long tagId = null;
        if (tagSlug != null && !tagSlug.isBlank()) {
            tagId = menuTaxonomyService.requireTagBySlug(tagSlug).getId();
        }
        Set<Long> normalizedTagIds = normalizeTagIds(tagIds);
        if (!normalizedTagIds.isEmpty()) {
            menuTaxonomyService.requireTags(normalizedTagIds);
        }
        Long allergenId = null;
        if (allergenSlug != null && !allergenSlug.isBlank()) {
            allergenId = menuTaxonomyService.requireAllergenBySlug(allergenSlug).getId();
        }
        Set<Long> normalizedAllergenIds = normalizeTagIds(allergenIds);
        if (!normalizedAllergenIds.isEmpty()) {
            menuTaxonomyService.requireAllergens(normalizedAllergenIds);
        }
        return MenuProductSpecifications.forMenuSearch(
                menuId,
                availableOnly,
                chefRecommended,
                minRating,
                tagId,
                normalizedTagIds,
                allergenId,
                normalizedAllergenIds,
                subCategoryId,
                mainCategoryId,
                servesPeople,
                servesPeopleMin,
                servesPeopleMax,
                q
        );
    }

    private MenuDtos.PublicMenuResponse buildPublicResponse(Menu menu) {
        MenuDtos.MenuProductPageResponse productPage = listAvailableProductsPage(
                menu,
                0,
                DEFAULT_PRODUCT_PAGE_SIZE
        );

        return MenuDtos.PublicMenuResponse.builder()
                .menu(toMenuProfile(menu, buildPublicUrl(menu), null))
                .products(productPage.getContent())
                .categories(menuTaxonomyService.listTaxonomy())
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
        Map<Long, SubCategory> subMap = menuTaxonomyService.loadSubCategoryMap();
        Map<Long, MainCategory> mainMap = menuTaxonomyService.loadMainCategoryMap();
        Map<Long, MenuTag> tagMap = menuTaxonomyService.loadTagMap();
        Map<Long, MenuAllergen> allergenMap = menuTaxonomyService.loadAllergenMap();
        List<MenuDtos.MenuProductResponse> content = productPage.getContent().stream()
                .map(product -> toProductResponse(product, subMap, mainMap, tagMap, allergenMap))
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
        return toProductResponse(
                product,
                menuTaxonomyService.loadSubCategoryMap(),
                menuTaxonomyService.loadMainCategoryMap(),
                menuTaxonomyService.loadTagMap(),
                menuTaxonomyService.loadAllergenMap()
        );
    }

    private MenuDtos.MenuProductResponse toProductResponse(
            MenuProduct product,
            Map<Long, SubCategory> subMap,
            Map<Long, MainCategory> mainMap,
            Map<Long, MenuTag> tagMap,
            Map<Long, MenuAllergen> allergenMap
    ) {
        SubCategory sub = subMap.get(product.getSubCategoryId());
        MainCategory main = sub == null ? null : mainMap.get(sub.getMainCategoryId());
        List<TaxonomyDtos.TagResponse> tags = (product.getTagIds() == null ? Set.<Long>of() : product.getTagIds())
                .stream()
                .map(tagMap::get)
                .filter(tag -> tag != null)
                .map(tag -> TaxonomyDtos.TagResponse.builder()
                        .id(tag.getId())
                        .slug(tag.getSlug())
                        .name(tag.getName())
                        .sortOrder(tag.getSortOrder())
                        .build())
                .toList();
        List<TaxonomyDtos.AllergenResponse> allergens =
                (product.getAllergenIds() == null ? Set.<Long>of() : product.getAllergenIds())
                        .stream()
                        .map(allergenMap::get)
                        .filter(allergen -> allergen != null)
                        .map(allergen -> TaxonomyDtos.AllergenResponse.builder()
                                .id(allergen.getId())
                                .slug(allergen.getSlug())
                                .name(allergen.getName())
                                .sortOrder(allergen.getSortOrder())
                                .build())
                        .toList();

        return MenuDtos.MenuProductResponse.builder()
                .productId(product.getProductId())
                .menuId(product.getMenuId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .subCategoryId(product.getSubCategoryId())
                .subCategorySlug(sub == null ? null : sub.getSlug())
                .subCategoryName(sub == null ? null : sub.getName())
                .mainCategoryId(main == null ? null : main.getId())
                .mainCategorySlug(main == null ? null : main.getSlug())
                .mainCategoryName(main == null ? null : main.getName())
                .tags(tags)
                .allergens(allergens)
                .sortOrder(product.getSortOrder())
                .imageUrl(product.getImageUrl())
                .available(product.isAvailable())
                .chefRecommended(product.isChefRecommended())
                .ratingAvg(product.getRatingAvg() == null ? BigDecimal.ZERO : product.getRatingAvg())
                .ratingCount(product.getRatingCount())
                .servesPeopleMin(product.getServesPeopleMin())
                .servesPeopleMax(product.getServesPeopleMax())
                .nutrition(product.getNutrition())
                .build();
    }

    private Set<Long> applyChefRecommended(Set<Long> tagIds, Boolean chefRecommended) {
        Set<Long> result = tagIds == null ? new HashSet<>() : new HashSet<>(tagIds);
        Long chefTagId = menuTaxonomyService.findTagBySlug(MenuTaxonomyService.CHEF_RECOMMENDED_TAG_SLUG)
                .map(MenuTag::getId)
                .orElse(null);
        if (chefTagId == null) {
            return result;
        }
        if (Boolean.TRUE.equals(chefRecommended)) {
            result.add(chefTagId);
        } else if (Boolean.FALSE.equals(chefRecommended)) {
            result.remove(chefTagId);
        }
        return result;
    }

    private boolean resolveChefRecommended(Set<Long> tagIds, Boolean chefRecommended) {
        if (chefRecommended != null) {
            return chefRecommended;
        }
        Long chefTagId = menuTaxonomyService.findTagBySlug(MenuTaxonomyService.CHEF_RECOMMENDED_TAG_SLUG)
                .map(MenuTag::getId)
                .orElse(null);
        return chefTagId != null && tagIds != null && tagIds.contains(chefTagId);
    }

    private Set<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(tagIds.stream().filter(id -> id != null).toList());
    }

    @SuppressWarnings("unchecked")
    private Set<Long> parseTagIds(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> ids = new HashSet<>();
        for (Object item : list) {
            Long id = longValue(item);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
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
                .publicUrl(publicUrl)
                .active(menu.isActive())
                .qr(qrBrief)
                .build();
    }

    private void validateProductRequest(MenuDtos.MenuProductRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürün adı zorunludur");
        }
        if (request.getSubCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subCategoryId zorunludur");
        }
        servesPeopleSupport.normalize(request.getServesPeopleMin(), request.getServesPeopleMax());
    }

    private String resolveProductImageUrl(String imageUrl) {
        String normalized = trimToNull(imageUrl);
        productImageStorageService.validateImageUrl(normalized);
        return normalized;
    }

    private Integer integerOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException exception) {
            return null;
        }
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
