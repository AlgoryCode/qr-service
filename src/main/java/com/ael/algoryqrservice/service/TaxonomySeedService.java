package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MainCategory;
import com.ael.algoryqrservice.model.MenuAllergen;
import com.ael.algoryqrservice.model.MenuTag;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.dto.TaxonomyDtos;
import com.ael.algoryqrservice.repository.MainCategoryRepository;
import com.ael.algoryqrservice.repository.MenuAllergenRepository;
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
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxonomySeedService {

    private static final String CLASSPATH_SEED = "seed/menu-taxonomy.json";

    private final MainCategoryRepository mainCategoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final MenuTagRepository menuTagRepository;
    private final MenuAllergenRepository menuAllergenRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void upsertClasspathSeed() {
        TaxonomyDtos.Document document = loadClasspathSeed();
        upsertDocument(document);
    }

    @Transactional
    public void upsertDocument(TaxonomyDtos.Document document) {
        if (document == null) {
            throw new BadRequestException("Taxonomy dokumani bos olamaz");
        }
        LocalDateTime now = LocalDateTime.now();
        for (TaxonomyDtos.MainSeed mainSeed : nullSafe(document.getMains())) {
            upsertMain(mainSeed, now);
            for (TaxonomyDtos.SubSeed subSeed : nullSafe(mainSeed.getSubs())) {
                upsertSub(mainSeed.getId(), subSeed, now);
            }
        }
        for (TaxonomyDtos.TagSeed tagSeed : nullSafe(document.getTags())) {
            upsertTag(tagSeed, now);
        }
        for (TaxonomyDtos.AllergenSeed allergenSeed : nullSafe(document.getAllergens())) {
            upsertAllergen(allergenSeed, now);
        }
        log.info("Menu taxonomy seed upsert completed");
    }

    public TaxonomyDtos.Document loadClasspathSeed() {
        try {
            ClassPathResource resource = new ClassPathResource(CLASSPATH_SEED);
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, TaxonomyDtos.Document.class);
            }
        } catch (Exception exception) {
            throw new BadRequestException("Taxonomy seed okunamadi: " + exception.getMessage());
        }
    }

    private void upsertMain(TaxonomyDtos.MainSeed seed, LocalDateTime now) {
        requireIdSlugName(seed.getId(), seed.getSlug(), seed.getName(), "main");
        MainCategory existing = mainCategoryRepository.findById(seed.getId()).orElse(null);
        if (existing == null) {
            mainCategoryRepository.save(MainCategory.builder()
                    .id(seed.getId())
                    .slug(normalizeSlug(seed.getSlug()))
                    .name(seed.getName().trim())
                    .sortOrder(seed.getSortOrder() == null ? 0 : seed.getSortOrder())
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(false)
                    .build());
            return;
        }
        existing.setSlug(normalizeSlug(seed.getSlug()));
        existing.setName(seed.getName().trim());
        existing.setSortOrder(seed.getSortOrder() == null ? existing.getSortOrder() : seed.getSortOrder());
        existing.setDeleted(false);
        existing.setUpdatedAt(now);
        mainCategoryRepository.save(existing);
    }

    private void upsertSub(Long mainId, TaxonomyDtos.SubSeed seed, LocalDateTime now) {
        requireIdSlugName(seed.getId(), seed.getSlug(), seed.getName(), "sub");
        SubCategory existing = subCategoryRepository.findById(seed.getId()).orElse(null);
        if (existing == null) {
            subCategoryRepository.save(SubCategory.builder()
                    .id(seed.getId())
                    .mainCategoryId(mainId)
                    .slug(normalizeSlug(seed.getSlug()))
                    .name(seed.getName().trim())
                    .sortOrder(seed.getSortOrder() == null ? 0 : seed.getSortOrder())
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(false)
                    .build());
            return;
        }
        existing.setMainCategoryId(mainId);
        existing.setSlug(normalizeSlug(seed.getSlug()));
        existing.setName(seed.getName().trim());
        existing.setSortOrder(seed.getSortOrder() == null ? existing.getSortOrder() : seed.getSortOrder());
        existing.setDeleted(false);
        existing.setUpdatedAt(now);
        subCategoryRepository.save(existing);
    }

    private void upsertTag(TaxonomyDtos.TagSeed seed, LocalDateTime now) {
        requireIdSlugName(seed.getId(), seed.getSlug(), seed.getName(), "tag");
        MenuTag existing = menuTagRepository.findById(seed.getId()).orElse(null);
        if (existing == null) {
            menuTagRepository.save(MenuTag.builder()
                    .id(seed.getId())
                    .slug(normalizeSlug(seed.getSlug()))
                    .name(seed.getName().trim())
                    .sortOrder(seed.getSortOrder() == null ? 0 : seed.getSortOrder())
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(false)
                    .build());
            return;
        }
        existing.setSlug(normalizeSlug(seed.getSlug()));
        existing.setName(seed.getName().trim());
        existing.setSortOrder(seed.getSortOrder() == null ? existing.getSortOrder() : seed.getSortOrder());
        existing.setDeleted(false);
        existing.setUpdatedAt(now);
        menuTagRepository.save(existing);
    }

    private void upsertAllergen(TaxonomyDtos.AllergenSeed seed, LocalDateTime now) {
        requireIdSlugName(seed.getId(), seed.getSlug(), seed.getName(), "allergen");
        MenuAllergen existing = menuAllergenRepository.findById(seed.getId()).orElse(null);
        if (existing == null) {
            menuAllergenRepository.save(MenuAllergen.builder()
                    .id(seed.getId())
                    .slug(normalizeSlug(seed.getSlug()))
                    .name(seed.getName().trim())
                    .sortOrder(seed.getSortOrder() == null ? 0 : seed.getSortOrder())
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(false)
                    .build());
            return;
        }
        existing.setSlug(normalizeSlug(seed.getSlug()));
        existing.setName(seed.getName().trim());
        existing.setSortOrder(seed.getSortOrder() == null ? existing.getSortOrder() : seed.getSortOrder());
        existing.setDeleted(false);
        existing.setUpdatedAt(now);
        menuAllergenRepository.save(existing);
    }

    private void requireIdSlugName(Long id, String slug, String name, String kind) {
        if (id == null) {
            throw new BadRequestException(kind + " id zorunludur");
        }
        if (slug == null || slug.isBlank()) {
            throw new BadRequestException(kind + " slug zorunludur");
        }
        if (name == null || name.isBlank()) {
            throw new BadRequestException(kind + " name zorunludur");
        }
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase();
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    @Component
    @Order(50)
    @RequiredArgsConstructor
    public static class TaxonomySeedRunner implements ApplicationRunner {
        private final TaxonomySeedService taxonomySeedService;

        @Override
        public void run(ApplicationArguments args) {
            taxonomySeedService.upsertClasspathSeed();
        }
    }
}
