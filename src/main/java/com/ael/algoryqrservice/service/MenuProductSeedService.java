package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuAllergen;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuTag;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.dto.MenuProductSeedDtos;
import com.ael.algoryqrservice.repository.MenuAllergenRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuTagRepository;
import com.ael.algoryqrservice.repository.SubCategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuProductSeedService {

    private static final String CLASSPATH_SEED = "seed/menu-products.json";

    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final MenuTagRepository menuTagRepository;
    private final MenuAllergenRepository menuAllergenRepository;
    private final NutritionFactsService nutritionFactsService;
    private final ServesPeopleSupport servesPeopleSupport;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    @Transactional
    public int seedFromClasspathIfEnabled() {
        if (!appProperties.getSeed().getMenuProducts().isEnabled()) {
            return 0;
        }
        MenuProductSeedDtos.Document document = loadClasspathSeed();
        return seedDocument(document, appProperties.getSeed().getMenuProducts().isOnlyIfEmpty());
    }

    @Transactional
    public int seedDocument(MenuProductSeedDtos.Document document, boolean onlyIfEmpty) {
        if (document == null || document.getMenuId() == null) {
            throw new BadRequestException("menuId zorunludur");
        }
        Long menuId = document.getMenuId();
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new BadRequestException("Menü bulunamadı: " + menuId));
        if (menu.isDeleted()) {
            throw new BadRequestException("Silinmiş menüye seed yazılamaz: " + menuId);
        }
        if (onlyIfEmpty && menuProductRepository.countByMenuIdAndDeletedFalse(menuId) > 0) {
            log.info("Menu product seed skipped; menu {} already has products", menuId);
            return 0;
        }

        int created = 0;
        List<MenuProductSeedDtos.ProductSeed> products =
                document.getProducts() == null ? List.of() : document.getProducts();
        for (MenuProductSeedDtos.ProductSeed seed : products) {
            if (seed.getName() == null || seed.getName().isBlank()) {
                throw new BadRequestException("Ürün adı zorunludur");
            }
            if (seed.getSubCategorySlug() == null || seed.getSubCategorySlug().isBlank()) {
                throw new BadRequestException("subCategorySlug zorunludur: " + seed.getName());
            }
            if (menuProductRepository.existsByMenuIdAndNameIgnoreCaseAndDeletedFalse(menuId, seed.getName().trim())) {
                continue;
            }
            SubCategory sub = subCategoryRepository.findBySlugAndDeletedFalse(seed.getSubCategorySlug().trim().toLowerCase())
                    .orElseThrow(() -> new BadRequestException(
                            "Alt kategori slug bulunamadı: " + seed.getSubCategorySlug()));
            Set<Long> tagIds = resolveTagIds(seed.getTagSlugs());
            Set<Long> allergenIds = resolveAllergenIds(seed.getAllergenSlugs());
            var nutrition = seed.getNutrition();
            nutritionFactsService.validateForCreate(nutrition);
            ServesPeopleSupport.Range serves = servesPeopleSupport.resolveFromSeed(
                    seed.getServesPeopleMin(),
                    seed.getServesPeopleMax(),
                    seed.getName()
            );

            MenuProduct product = MenuProduct.builder()
                    .menuId(menuId)
                    .name(seed.getName().trim())
                    .description(trimToNull(seed.getDescription()))
                    .price(seed.getPrice())
                    .currency(seed.getCurrency() == null || seed.getCurrency().isBlank() ? "TRY" : seed.getCurrency().trim())
                    .subCategoryId(sub.getId())
                    .tagIds(tagIds)
                    .allergenIds(allergenIds)
                    .sortOrder(seed.getSortOrder() == null ? created : seed.getSortOrder())
                    .available(seed.getAvailable() == null || seed.getAvailable())
                    .servesPeopleMin(serves.min())
                    .servesPeopleMax(serves.max())
                    .nutrition(nutrition)
                    .build();
            menuProductRepository.save(product);
            created++;
        }
        log.info("Menu product seed completed for menu {}: {} products created", menuId, created);
        return created;
    }

    public MenuProductSeedDtos.Document loadClasspathSeed() {
        try {
            ClassPathResource resource = new ClassPathResource(CLASSPATH_SEED);
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, MenuProductSeedDtos.Document.class);
            }
        } catch (Exception exception) {
            throw new BadRequestException("Menu product seed okunamadı: " + exception.getMessage());
        }
    }

    private Set<Long> resolveTagIds(List<String> tagSlugs) {
        if (tagSlugs == null || tagSlugs.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> ids = new HashSet<>();
        for (String slug : tagSlugs) {
            if (slug == null || slug.isBlank()) {
                continue;
            }
            MenuTag tag = menuTagRepository.findBySlugAndDeletedFalse(slug.trim().toLowerCase())
                    .orElseThrow(() -> new BadRequestException("Tag slug bulunamadı: " + slug));
            ids.add(tag.getId());
        }
        return ids;
    }

    private Set<Long> resolveAllergenIds(List<String> allergenSlugs) {
        if (allergenSlugs == null || allergenSlugs.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> ids = new HashSet<>();
        for (String slug : allergenSlugs) {
            if (slug == null || slug.isBlank()) {
                continue;
            }
            MenuAllergen allergen = menuAllergenRepository.findBySlugAndDeletedFalse(slug.trim().toLowerCase())
                    .orElseThrow(() -> new BadRequestException("Allergen slug bulunamadı: " + slug));
            ids.add(allergen.getId());
        }
        return ids;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Component
    @Order(60)
    @RequiredArgsConstructor
    public static class MenuProductSeedRunner implements ApplicationRunner {
        private final MenuProductSeedService menuProductSeedService;

        @Override
        public void run(ApplicationArguments args) {
            menuProductSeedService.seedFromClasspathIfEnabled();
        }
    }
}
