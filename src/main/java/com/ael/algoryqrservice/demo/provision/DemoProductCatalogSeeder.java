package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.service.MenuCategoryService;
import com.ael.algoryqrservice.service.NutritionFactsService;
import com.ael.algoryqrservice.service.ServesPeopleSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DemoProductCatalogSeeder {

    private final DemoProductCatalogFixture catalogFixture;
    private final MenuCategoryService menuCategoryService;
    private final MenuProductRepository menuProductRepository;
    private final EntitlementService entitlementService;
    private final NutritionFactsService nutritionFactsService;
    private final ServesPeopleSupport servesPeopleSupport;

    @Transactional
    public int seedCatalog(Long menuId, Long userId) {
        if (menuId == null || userId == null) {
            return 0;
        }
        if (menuProductRepository.countByMenuIdAndDeletedFalse(menuId) > 0) {
            return (int) menuProductRepository.countByMenuIdAndDeletedFalse(menuId);
        }

        DemoProductCatalogDocument document = catalogFixture.get();
        List<DemoProductCatalogDocument.ProductSeed> products = document.products();
        if (products.isEmpty()) {
            throw new BadRequestException("Demo urun katalogu bos");
        }

        Map<String, Long> subIdBySlug = ensureTaxonomy(menuId, document.categories());
        entitlementService.assertMenuProductCreationAllowed(userId, products.size());

        int created = 0;
        for (DemoProductCatalogDocument.ProductSeed seed : products) {
            if (seed.name() == null || seed.name().isBlank()) {
                continue;
            }
            String subSlug = normalizeSlug(seed.subCategorySlug());
            Long subCategoryId = subIdBySlug.get(subSlug);
            if (subCategoryId == null) {
                throw new BadRequestException("Demo alt kategori bulunamadi: " + seed.subCategorySlug());
            }
            if (menuProductRepository.existsByMenuIdAndNameIgnoreCaseAndDeletedFalse(menuId, seed.name().trim())) {
                continue;
            }
            NutritionFacts nutrition = seed.nutrition() != null
                    ? seed.nutrition()
                    : DemoProductCatalogNutritionDefaults.resolve(seed.nutritionProfile(), subSlug);
            nutritionFactsService.validateForCreate(nutrition);
            ServesPeopleSupport.Range serves = servesPeopleSupport.resolveFromSeed(
                    seed.servesPeopleMin(),
                    seed.servesPeopleMax(),
                    seed.name()
            );

            MenuProduct product = MenuProduct.builder()
                    .menuId(menuId)
                    .name(seed.name().trim())
                    .description(trimToNull(seed.description()))
                    .price(seed.price())
                    .currency(seed.currency() == null || seed.currency().isBlank() ? "TRY" : seed.currency().trim())
                    .subCategoryId(subCategoryId)
                    .tagIds(new HashSet<>())
                    .allergenIds(new HashSet<>())
                    .sortOrder(seed.sortOrder() == null ? created : seed.sortOrder())
                    .available(seed.available() == null || seed.available())
                    .nutrition(nutrition)
                    .servesPeopleMin(serves.min())
                    .servesPeopleMax(serves.max())
                    .build();
            menuProductRepository.save(product);
            created++;
        }
        return created;
    }

    private Map<String, Long> ensureTaxonomy(Long menuId, List<DemoProductCatalogDocument.CategorySeed> categories) {
        Map<String, Long> subIdBySlug = new HashMap<>();
        Set<String> seenSubSlugs = new HashSet<>();
        for (DemoProductCatalogDocument.CategorySeed category : categories) {
            if (category.name() == null || category.name().isBlank()) {
                continue;
            }
            TaxonomyDtos.MainCategoryRequest mainRequest = TaxonomyDtos.MainCategoryRequest.builder()
                    .name(category.name().trim())
                    .slug(normalizeSlug(category.slug()))
                    .sortOrder(category.sortOrder() == null ? 0 : category.sortOrder())
                    .build();
            TaxonomyDtos.MainCategoryResponse main = menuCategoryService.createCategory(menuId, mainRequest);
            for (DemoProductCatalogDocument.SubCategorySeed sub : category.subCategories()) {
                if (sub.name() == null || sub.name().isBlank()) {
                    continue;
                }
                String slug = normalizeSlug(sub.slug());
                if (!seenSubSlugs.add(slug)) {
                    continue;
                }
                TaxonomyDtos.SubCategoryRequest subRequest = TaxonomyDtos.SubCategoryRequest.builder()
                        .name(sub.name().trim())
                        .slug(slug)
                        .sortOrder(sub.sortOrder() == null ? 0 : sub.sortOrder())
                        .build();
                TaxonomyDtos.SubCategoryResponse saved = menuCategoryService.createSub(menuId, main.getId(), subRequest);
                subIdBySlug.put(slug, saved.getId());
            }
        }
        if (subIdBySlug.isEmpty()) {
            throw new BadRequestException("Demo kategori yapisi olusturulamadi");
        }
        return subIdBySlug;
    }

    private static String normalizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "";
        }
        return slug.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
