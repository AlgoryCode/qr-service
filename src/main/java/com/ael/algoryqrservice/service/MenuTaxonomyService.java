package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MainCategory;
import com.ael.algoryqrservice.model.MenuAllergen;
import com.ael.algoryqrservice.model.MenuTag;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.repository.MainCategoryRepository;
import com.ael.algoryqrservice.repository.MenuAllergenRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuTagRepository;
import com.ael.algoryqrservice.repository.SubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuTaxonomyService {

    private final MainCategoryRepository mainCategoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final MenuTagRepository menuTagRepository;
    private final MenuAllergenRepository menuAllergenRepository;
    private final MenuProductRepository menuProductRepository;

    @Transactional(readOnly = true)
    public List<TaxonomyDtos.MainCategoryResponse> listTaxonomy() {
        List<MainCategory> mains = mainCategoryRepository.findByDeletedFalseOrderBySortOrderAscIdAsc();
        List<SubCategory> subs = subCategoryRepository.findByDeletedFalseOrderBySortOrderAscIdAsc();
        Map<Long, List<SubCategory>> byMain = subs.stream()
                .collect(Collectors.groupingBy(SubCategory::getMainCategoryId));
        List<TaxonomyDtos.MainCategoryResponse> result = new ArrayList<>();
        for (MainCategory main : mains) {
            result.add(TaxonomyDtos.MainCategoryResponse.builder()
                    .id(main.getId())
                    .slug(main.getSlug())
                    .name(main.getName())
                    .sortOrder(main.getSortOrder())
                    .subs(byMain.getOrDefault(main.getId(), List.of()).stream()
                            .map(this::toSubResponse)
                            .toList())
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TaxonomyDtos.TagResponse> listTags() {
        return menuTagRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .map(this::toTagResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxonomyDtos.AllergenResponse> listAllergens() {
        return menuAllergenRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .map(this::toAllergenResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubCategory requireSubCategory(Long subCategoryId) {
        if (subCategoryId == null) {
            throw new BadRequestException("subCategoryId zorunludur");
        }
        return subCategoryRepository.findByIdAndDeletedFalse(subCategoryId)
                .orElseThrow(() -> new BadRequestException("Alt kategori bulunamadi: " + subCategoryId));
    }

    @Transactional(readOnly = true)
    public MainCategory requireMainCategory(Long mainCategoryId) {
        return mainCategoryRepository.findByIdAndDeletedFalse(mainCategoryId)
                .orElseThrow(() -> new BadRequestException("Ana kategori bulunamadi: " + mainCategoryId));
    }

    @Transactional(readOnly = true)
    public List<MenuTag> requireTags(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        List<MenuTag> tags = menuTagRepository.findByIdInAndDeletedFalse(tagIds);
        if (tags.size() != tagIds.stream().filter(Objects::nonNull).distinct().count()) {
            throw new BadRequestException("Gecersiz tag id");
        }
        return tags;
    }

    @Transactional(readOnly = true)
    public List<MenuAllergen> requireAllergens(Collection<Long> allergenIds) {
        if (allergenIds == null || allergenIds.isEmpty()) {
            return List.of();
        }
        List<MenuAllergen> allergens = menuAllergenRepository.findByIdInAndDeletedFalse(allergenIds);
        if (allergens.size() != allergenIds.stream().filter(Objects::nonNull).distinct().count()) {
            throw new BadRequestException("Gecersiz allergen id");
        }
        return allergens;
    }

    @Transactional(readOnly = true)
    public Map<Long, SubCategory> loadSubCategoryMap() {
        return subCategoryRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.toMap(SubCategory::getId, s -> s, (a, b) -> a, HashMap::new));
    }

    @Transactional(readOnly = true)
    public Map<Long, MainCategory> loadMainCategoryMap() {
        return mainCategoryRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.toMap(MainCategory::getId, m -> m, (a, b) -> a, HashMap::new));
    }

    @Transactional(readOnly = true)
    public Map<Long, MenuTag> loadTagMap() {
        return menuTagRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.toMap(MenuTag::getId, t -> t, (a, b) -> a, HashMap::new));
    }

    @Transactional(readOnly = true)
    public Map<Long, MenuAllergen> loadAllergenMap() {
        return menuAllergenRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.toMap(MenuAllergen::getId, a -> a, (a, b) -> a, HashMap::new));
    }

    public static final String CHEF_RECOMMENDED_TAG_SLUG = "sef_ozel";

    @Transactional(readOnly = true)
    public Optional<MenuTag> findTagBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return menuTagRepository.findBySlugAndDeletedFalse(slug.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public MenuTag requireTagBySlug(String slug) {
        return findTagBySlug(slug)
                .orElseThrow(() -> new BadRequestException("Tag slug bulunamadi: " + slug));
    }

    @Transactional(readOnly = true)
    public Optional<MenuAllergen> findAllergenBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return menuAllergenRepository.findBySlugAndDeletedFalse(slug.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public MenuAllergen requireAllergenBySlug(String slug) {
        return findAllergenBySlug(slug)
                .orElseThrow(() -> new BadRequestException("Allergen slug bulunamadi: " + slug));
    }

    @Transactional(readOnly = true)
    public SubCategory resolveSubCategoryBySlug(String slug) {
        String normalized = requireSlug(slug);
        return subCategoryRepository.findBySlugAndDeletedFalse(normalized)
                .orElseThrow(() -> new BadRequestException("Alt kategori slug bulunamadi: " + normalized));
    }

    @Transactional(readOnly = true)
    public MainCategory resolveMainCategoryBySlug(String slug) {
        String normalized = requireSlug(slug);
        return mainCategoryRepository.findBySlugAndDeletedFalse(normalized)
                .orElseThrow(() -> new BadRequestException("Ana kategori slug bulunamadi: " + normalized));
    }

    @Transactional
    public TaxonomyDtos.MainCategoryResponse createMain(TaxonomyDtos.MainCategoryRequest request) {
        Long id = request.getId() != null ? request.getId() : nextMainId();
        String slug = requireSlug(request.getSlug());
        if (mainCategoryRepository.existsBySlugAndDeletedFalse(slug)) {
            throw new BadRequestException("Main slug zaten var: " + slug);
        }
        if (mainCategoryRepository.existsById(id)) {
            throw new BadRequestException("Main id zaten var: " + id);
        }
        MainCategory saved = mainCategoryRepository.save(MainCategory.builder()
                .id(id)
                .slug(slug)
                .name(requireName(request.getName()))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build());
        return TaxonomyDtos.MainCategoryResponse.builder()
                .id(saved.getId())
                .slug(saved.getSlug())
                .name(saved.getName())
                .sortOrder(saved.getSortOrder())
                .subs(List.of())
                .build();
    }

    @Transactional
    public TaxonomyDtos.MainCategoryResponse updateMain(Long id, TaxonomyDtos.MainCategoryUpdateRequest request) {
        MainCategory main = requireMainCategory(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            main.setName(request.getName().trim());
        }
        if (request.getSortOrder() != null) {
            main.setSortOrder(request.getSortOrder());
        }
        main.setUpdatedAt(LocalDateTime.now());
        mainCategoryRepository.save(main);
        return listTaxonomy().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void deleteMain(Long id) {
        MainCategory main = requireMainCategory(id);
        if (!subCategoryRepository.findByMainCategoryIdAndDeletedFalseOrderBySortOrderAscIdAsc(id).isEmpty()) {
            throw new BadRequestException("Alt kategorisi olan ana kategori silinemez");
        }
        main.setDeleted(true);
        main.setUpdatedAt(LocalDateTime.now());
        mainCategoryRepository.save(main);
    }

    @Transactional
    public TaxonomyDtos.SubCategoryResponse createSub(TaxonomyDtos.SubCategoryRequest request) {
        requireMainCategory(request.getMainCategoryId());
        Long id = request.getId() != null ? request.getId() : nextSubId();
        String slug = requireSlug(request.getSlug());
        if (subCategoryRepository.existsBySlugAndDeletedFalse(slug)) {
            throw new BadRequestException("Sub slug zaten var: " + slug);
        }
        if (subCategoryRepository.existsById(id)) {
            throw new BadRequestException("Sub id zaten var: " + id);
        }
        SubCategory saved = subCategoryRepository.save(SubCategory.builder()
                .id(id)
                .mainCategoryId(request.getMainCategoryId())
                .slug(slug)
                .name(requireName(request.getName()))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build());
        return toSubResponse(saved);
    }

    @Transactional
    public TaxonomyDtos.SubCategoryResponse updateSub(Long id, TaxonomyDtos.SubCategoryUpdateRequest request) {
        SubCategory sub = requireSubCategory(id);
        if (request.getMainCategoryId() != null) {
            requireMainCategory(request.getMainCategoryId());
            sub.setMainCategoryId(request.getMainCategoryId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            sub.setName(request.getName().trim());
        }
        if (request.getSortOrder() != null) {
            sub.setSortOrder(request.getSortOrder());
        }
        sub.setUpdatedAt(LocalDateTime.now());
        return toSubResponse(subCategoryRepository.save(sub));
    }

    @Transactional
    public void deleteSub(Long id) {
        SubCategory sub = requireSubCategory(id);
        if (menuProductRepository.countBySubCategoryIdAndDeletedFalse(id) > 0) {
            throw new BadRequestException("Urunu olan alt kategori silinemez");
        }
        sub.setDeleted(true);
        sub.setUpdatedAt(LocalDateTime.now());
        subCategoryRepository.save(sub);
    }

    @Transactional
    public TaxonomyDtos.TagResponse createTag(TaxonomyDtos.TagRequest request) {
        Long id = request.getId() != null ? request.getId() : nextTagId();
        String slug = requireSlug(request.getSlug());
        if (menuTagRepository.existsBySlugAndDeletedFalse(slug)) {
            throw new BadRequestException("Tag slug zaten var: " + slug);
        }
        if (menuTagRepository.existsById(id)) {
            throw new BadRequestException("Tag id zaten var: " + id);
        }
        MenuTag saved = menuTagRepository.save(MenuTag.builder()
                .id(id)
                .slug(slug)
                .name(requireName(request.getName()))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build());
        return toTagResponse(saved);
    }

    @Transactional
    public TaxonomyDtos.TagResponse updateTag(Long id, TaxonomyDtos.TagUpdateRequest request) {
        MenuTag tag = menuTagRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag bulunamadi"));
        if (request.getName() != null && !request.getName().isBlank()) {
            tag.setName(request.getName().trim());
        }
        if (request.getSortOrder() != null) {
            tag.setSortOrder(request.getSortOrder());
        }
        tag.setUpdatedAt(LocalDateTime.now());
        return toTagResponse(menuTagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(Long id) {
        MenuTag tag = menuTagRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag bulunamadi"));
        tag.setDeleted(true);
        tag.setUpdatedAt(LocalDateTime.now());
        menuTagRepository.save(tag);
    }

    @Transactional
    public TaxonomyDtos.AllergenResponse createAllergen(TaxonomyDtos.AllergenRequest request) {
        Long id = request.getId() != null ? request.getId() : nextAllergenId();
        String slug = requireSlug(request.getSlug());
        if (menuAllergenRepository.existsBySlugAndDeletedFalse(slug)) {
            throw new BadRequestException("Allergen slug zaten var: " + slug);
        }
        if (menuAllergenRepository.existsById(id)) {
            throw new BadRequestException("Allergen id zaten var: " + id);
        }
        MenuAllergen saved = menuAllergenRepository.save(MenuAllergen.builder()
                .id(id)
                .slug(slug)
                .name(requireName(request.getName()))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build());
        return toAllergenResponse(saved);
    }

    @Transactional
    public TaxonomyDtos.AllergenResponse updateAllergen(Long id, TaxonomyDtos.AllergenUpdateRequest request) {
        MenuAllergen allergen = menuAllergenRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Allergen bulunamadi"));
        if (request.getName() != null && !request.getName().isBlank()) {
            allergen.setName(request.getName().trim());
        }
        if (request.getSortOrder() != null) {
            allergen.setSortOrder(request.getSortOrder());
        }
        allergen.setUpdatedAt(LocalDateTime.now());
        return toAllergenResponse(menuAllergenRepository.save(allergen));
    }

    @Transactional
    public void deleteAllergen(Long id) {
        MenuAllergen allergen = menuAllergenRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Allergen bulunamadi"));
        allergen.setDeleted(true);
        allergen.setUpdatedAt(LocalDateTime.now());
        menuAllergenRepository.save(allergen);
    }

    private long nextMainId() {
        return mainCategoryRepository.findAll().stream().mapToLong(MainCategory::getId).max().orElse(0L) + 1L;
    }

    private long nextSubId() {
        return subCategoryRepository.findAll().stream().mapToLong(SubCategory::getId).max().orElse(0L) + 1L;
    }

    private long nextTagId() {
        return menuTagRepository.findAll().stream().mapToLong(MenuTag::getId).max().orElse(0L) + 1L;
    }

    private long nextAllergenId() {
        return menuAllergenRepository.findAll().stream().mapToLong(MenuAllergen::getId).max().orElse(0L) + 1L;
    }

    private TaxonomyDtos.SubCategoryResponse toSubResponse(SubCategory sub) {
        return TaxonomyDtos.SubCategoryResponse.builder()
                .id(sub.getId())
                .mainCategoryId(sub.getMainCategoryId())
                .slug(sub.getSlug())
                .name(sub.getName())
                .sortOrder(sub.getSortOrder())
                .build();
    }

    private TaxonomyDtos.TagResponse toTagResponse(MenuTag tag) {
        return TaxonomyDtos.TagResponse.builder()
                .id(tag.getId())
                .slug(tag.getSlug())
                .name(tag.getName())
                .sortOrder(tag.getSortOrder())
                .build();
    }

    private TaxonomyDtos.AllergenResponse toAllergenResponse(MenuAllergen allergen) {
        return TaxonomyDtos.AllergenResponse.builder()
                .id(allergen.getId())
                .slug(allergen.getSlug())
                .name(allergen.getName())
                .sortOrder(allergen.getSortOrder())
                .build();
    }

    private String requireSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new BadRequestException("slug zorunludur");
        }
        String normalized = slug.trim().toLowerCase();
        if (!normalized.matches("^[a-z0-9]+(?:_[a-z0-9]+)*$")) {
            throw new BadRequestException("slug formati gecersiz");
        }
        return normalized;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name zorunludur");
        }
        return name.trim();
    }
}
