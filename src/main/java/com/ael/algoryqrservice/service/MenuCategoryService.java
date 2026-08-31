package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.dto.ProductImageDtos;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.repository.MenuCategoryRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuCategoryService {

    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuSubCategoryRepository menuSubCategoryRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuRepository menuRepository;
    private final ProductImageStorageService productImageStorageService;

    @Transactional(readOnly = true)
    public List<TaxonomyDtos.MainCategoryResponse> listTaxonomy(Long menuId) {
        List<MenuCategory> categories = menuCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menuId);
        Map<Long, List<MenuSubCategory>> byParent = menuSubCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menuId)
                .stream()
                .collect(Collectors.groupingBy(MenuSubCategory::getMenuCategoryId));
        List<TaxonomyDtos.MainCategoryResponse> result = new ArrayList<>();
        for (MenuCategory category : categories) {
            result.add(toMainResponse(category, byParent.getOrDefault(category.getId(), List.of())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TaxonomyDtos.TaxonomyPageResponse listTaxonomyPage(Long menuId, int page, int size, String q) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String query = q == null ? "" : q.trim();
        boolean qBlank = query.isEmpty();
        String needle = query.toLowerCase(Locale.ROOT);
        List<TaxonomyDtos.MainCategoryResponse> all = listTaxonomy(menuId);
        List<TaxonomyDtos.MainCategoryResponse> filtered = new ArrayList<>();
        for (TaxonomyDtos.MainCategoryResponse main : all) {
            boolean mainMatches = qBlank
                    || containsIgnoreCase(main.getName(), needle)
                    || containsIgnoreCase(main.getSlug(), needle);
            List<TaxonomyDtos.SubCategoryResponse> visibleSubs = qBlank || mainMatches
                    ? main.getSubs()
                    : main.getSubs().stream()
                    .filter(sub -> containsIgnoreCase(sub.getName(), needle)
                            || containsIgnoreCase(sub.getSlug(), needle))
                    .toList();
            if (!qBlank && !mainMatches && visibleSubs.isEmpty()) {
                continue;
            }
            filtered.add(TaxonomyDtos.MainCategoryResponse.builder()
                    .id(main.getId())
                    .menuId(main.getMenuId())
                    .userId(main.getUserId())
                    .slug(main.getSlug())
                    .name(main.getName())
                    .sortOrder(main.getSortOrder())
                    .imageUrl(main.getImageUrl())
                    .subs(visibleSubs)
                    .build());
        }
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        List<TaxonomyDtos.MainCategoryResponse> content = filtered.subList(from, to);
        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil(filtered.size() / (double) safeSize);
        return TaxonomyDtos.TaxonomyPageResponse.builder()
                .content(content)
                .page(safePage)
                .size(safeSize)
                .totalElements(filtered.size())
                .totalPages(totalPages)
                .hasNext(safePage + 1 < totalPages)
                .q(qBlank ? null : query)
                .build();
    }

    @Transactional(readOnly = true)
    public MenuCategory requireCategory(Long menuId, Long categoryId) {
        return menuCategoryRepository.findByIdAndMenuIdAndDeletedFalse(categoryId, menuId)
                .orElseThrow(() -> new BadRequestException("Kategori bulunamadi: " + categoryId));
    }

    @Transactional(readOnly = true)
    public MenuSubCategory requireSubCategory(Long menuId, Long subCategoryId) {
        if (subCategoryId == null) {
            throw new BadRequestException("subCategoryId zorunludur");
        }
        return menuSubCategoryRepository.findByIdAndMenuIdAndDeletedFalse(subCategoryId, menuId)
                .orElseThrow(() -> new BadRequestException("Alt kategori bulunamadi: " + subCategoryId));
    }

    @Transactional(readOnly = true)
    public MenuSubCategory requireSubCategory(Long subCategoryId) {
        if (subCategoryId == null) {
            throw new BadRequestException("subCategoryId zorunludur");
        }
        return menuSubCategoryRepository.findByIdAndDeletedFalse(subCategoryId)
                .orElseThrow(() -> new BadRequestException("Alt kategori bulunamadi: " + subCategoryId));
    }

    @Transactional(readOnly = true)
    public MenuCategory requireCategory(Long categoryId) {
        return menuCategoryRepository.findById(categoryId)
                .filter(category -> !category.isDeleted())
                .orElseThrow(() -> new BadRequestException("Kategori bulunamadi: " + categoryId));
    }

    @Transactional(readOnly = true)
    public Map<Long, MenuSubCategory> loadSubCategoryMap(Long menuId) {
        return menuSubCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menuId).stream()
                .collect(Collectors.toMap(MenuSubCategory::getId, sub -> sub, (a, b) -> a, HashMap::new));
    }

    @Transactional(readOnly = true)
    public Map<Long, MenuCategory> loadCategoryMap(Long menuId) {
        return menuCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menuId).stream()
                .collect(Collectors.toMap(MenuCategory::getId, category -> category, (a, b) -> a, HashMap::new));
    }

    @Transactional
    public TaxonomyDtos.MainCategoryResponse createCategory(Long menuId, TaxonomyDtos.MainCategoryRequest request) {
        Long userId = requireMenuUserId(menuId);
        String name = requireName(request.getName());
        String slug = resolveSlug(menuId, request.getSlug(), name, true);
        MenuCategory saved = menuCategoryRepository.save(MenuCategory.builder()
                .menuId(menuId)
                .userId(userId)
                .slug(slug)
                .name(name)
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build());
        return toMainResponse(saved, List.of());
    }

    @Transactional
    public TaxonomyDtos.MainCategoryResponse updateCategory(
            Long menuId,
            Long categoryId,
            TaxonomyDtos.MainCategoryUpdateRequest request
    ) {
        MenuCategory category = requireCategory(menuId, categoryId);
        if (request.getName() != null && !request.getName().isBlank()) {
            category.setName(request.getName().trim());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        category.setUpdatedAt(LocalDateTime.now());
        menuCategoryRepository.save(category);
        return listTaxonomy(menuId).stream()
                .filter(item -> item.getId().equals(categoryId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void deleteCategory(Long menuId, Long categoryId) {
        MenuCategory category = requireCategory(menuId, categoryId);
        if (menuSubCategoryRepository.countByMenuCategoryIdAndDeletedFalse(categoryId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alt kategorisi olan kategori silinemez");
        }
        String previousKey = category.getImageKey();
        category.setDeleted(true);
        category.setUpdatedAt(LocalDateTime.now());
        menuCategoryRepository.save(category);
        productImageStorageService.deleteQuietly(previousKey);
    }

    @Transactional
    public TaxonomyDtos.MainCategoryResponse uploadCover(Long menuId, Long categoryId, MultipartFile file) {
        MenuCategory category = requireCategory(menuId, categoryId);
        ProductImageDtos.UploadResponse uploaded = productImageStorageService.uploadCategoryCover(menuId, categoryId, file);
        String previousKey = category.getImageKey();
        category.setImageUrl(uploaded.imageUrl());
        category.setImageKey(uploaded.objectKey());
        category.setUpdatedAt(LocalDateTime.now());
        menuCategoryRepository.save(category);
        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(uploaded.objectKey())) {
            productImageStorageService.deleteQuietly(previousKey);
        }
        return toMainResponse(category, List.of());
    }

    @Transactional
    public TaxonomyDtos.MainCategoryResponse clearCover(Long menuId, Long categoryId) {
        MenuCategory category = requireCategory(menuId, categoryId);
        String previousKey = category.getImageKey();
        category.setImageUrl(null);
        category.setImageKey(null);
        category.setUpdatedAt(LocalDateTime.now());
        menuCategoryRepository.save(category);
        productImageStorageService.deleteQuietly(previousKey);
        return toMainResponse(category, List.of());
    }

    @Transactional
    public TaxonomyDtos.SubCategoryResponse createSub(
            Long menuId,
            Long categoryId,
            TaxonomyDtos.SubCategoryRequest request
    ) {
        requireCategory(menuId, categoryId);
        String name = requireName(request.getName());
        String slug = resolveSlug(menuId, request.getSlug(), name, false);
        MenuSubCategory saved = menuSubCategoryRepository.save(MenuSubCategory.builder()
                .menuId(menuId)
                .menuCategoryId(categoryId)
                .slug(slug)
                .name(name)
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build());
        return toSubResponse(saved);
    }

    @Transactional
    public TaxonomyDtos.SubCategoryResponse updateSub(
            Long menuId,
            Long subId,
            TaxonomyDtos.SubCategoryUpdateRequest request
    ) {
        MenuSubCategory sub = requireSubCategory(menuId, subId);
        if (request.getMainCategoryId() != null) {
            requireCategory(menuId, request.getMainCategoryId());
            sub.setMenuCategoryId(request.getMainCategoryId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            sub.setName(request.getName().trim());
        }
        if (request.getSortOrder() != null) {
            sub.setSortOrder(request.getSortOrder());
        }
        sub.setUpdatedAt(LocalDateTime.now());
        return toSubResponse(menuSubCategoryRepository.save(sub));
    }

    @Transactional
    public void deleteSub(Long menuId, Long subId) {
        MenuSubCategory sub = requireSubCategory(menuId, subId);
        if (menuProductRepository.countBySubCategoryIdAndDeletedFalse(subId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Urunu olan alt kategori silinemez");
        }
        sub.setDeleted(true);
        sub.setUpdatedAt(LocalDateTime.now());
        menuSubCategoryRepository.save(sub);
    }

    public record TaxonomyCloneResult(Map<Long, Long> categoryIds, Map<Long, Long> subCategoryIds) {
    }

    @Transactional
    public TaxonomyCloneResult cloneTaxonomyToMenu(Long sourceMenuId, Long targetMenuId) {
        Long targetUserId = requireMenuUserId(targetMenuId);
        Map<Long, Long> sourceSubToTargetSub = new HashMap<>();
        Map<Long, Long> sourceCategoryToTarget = new HashMap<>();
        List<MenuCategory> sourceCategories = menuCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(sourceMenuId);
        for (MenuCategory source : sourceCategories) {
            MenuCategory copied = menuCategoryRepository.save(MenuCategory.builder()
                    .menuId(targetMenuId)
                    .userId(targetUserId)
                    .slug(source.getSlug())
                    .name(source.getName())
                    .sortOrder(source.getSortOrder())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .deleted(false)
                    .build());
            sourceCategoryToTarget.put(source.getId(), copied.getId());
        }
        List<MenuSubCategory> sourceSubs = menuSubCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(sourceMenuId);
        for (MenuSubCategory source : sourceSubs) {
            Long targetCategoryId = sourceCategoryToTarget.get(source.getMenuCategoryId());
            if (targetCategoryId == null) {
                continue;
            }
            MenuSubCategory copied = menuSubCategoryRepository.save(MenuSubCategory.builder()
                    .menuId(targetMenuId)
                    .menuCategoryId(targetCategoryId)
                    .slug(source.getSlug())
                    .name(source.getName())
                    .sortOrder(source.getSortOrder())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .deleted(false)
                    .build());
            sourceSubToTargetSub.put(source.getId(), copied.getId());
        }
        return new TaxonomyCloneResult(sourceCategoryToTarget, sourceSubToTargetSub);
    }

    private Long requireMenuUserId(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (menu.getUserId() == null) {
            throw new BadRequestException("Menü sahibi bulunamadı");
        }
        return menu.getUserId();
    }

    private TaxonomyDtos.MainCategoryResponse toMainResponse(MenuCategory category, List<MenuSubCategory> subs) {
        return TaxonomyDtos.MainCategoryResponse.builder()
                .id(category.getId())
                .menuId(category.getMenuId())
                .userId(category.getUserId())
                .slug(category.getSlug())
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .imageUrl(category.getImageUrl())
                .subs(subs.stream().map(this::toSubResponse).toList())
                .build();
    }

    private TaxonomyDtos.SubCategoryResponse toSubResponse(MenuSubCategory sub) {
        return TaxonomyDtos.SubCategoryResponse.builder()
                .id(sub.getId())
                .mainCategoryId(sub.getMenuCategoryId())
                .slug(sub.getSlug())
                .name(sub.getName())
                .sortOrder(sub.getSortOrder())
                .descriptors(List.of())
                .build();
    }

    private String resolveSlug(Long menuId, String requested, String name, boolean forCategory) {
        String base = requested == null || requested.isBlank() ? slugify(name) : requireSlug(requested);
        if (forCategory) {
            if (menuCategoryRepository.existsByMenuIdAndSlugAndDeletedFalse(menuId, base)) {
                throw new BadRequestException("Kategori slug zaten var: " + base);
            }
            return base;
        }
        if (menuSubCategoryRepository.existsByMenuIdAndSlugAndDeletedFalse(menuId, base)) {
            throw new BadRequestException("Alt kategori slug zaten var: " + base);
        }
        return base;
    }

    private static String slugify(String name) {
        String normalized = Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('ı', 'i')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ş', 's')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new BadRequestException("slug uretilemedi");
        }
        return normalized;
    }

    private static String requireSlug(String slug) {
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9]+(?:_[a-z0-9]+)*$")) {
            throw new BadRequestException("slug formati gecersiz");
        }
        return normalized;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name zorunludur");
        }
        return name.trim();
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
