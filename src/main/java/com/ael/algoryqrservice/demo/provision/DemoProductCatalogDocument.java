package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DemoProductCatalogDocument(
        List<CategorySeed> categories,
        List<ProductSeed> products
) {
    public DemoProductCatalogDocument {
        if (categories == null) {
            categories = List.of();
        }
        if (products == null) {
            products = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategorySeed(
            String slug,
            String name,
            Integer sortOrder,
            List<SubCategorySeed> subCategories
    ) {
        public CategorySeed {
            if (subCategories == null) {
                subCategories = List.of();
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubCategorySeed(String slug, String name, Integer sortOrder) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductSeed(
            String name,
            String description,
            BigDecimal price,
            String currency,
            String subCategorySlug,
            Integer sortOrder,
            Boolean available,
            String nutritionProfile,
            NutritionFacts nutrition,
            Integer servesPeopleMin,
            Integer servesPeopleMax
    ) {
    }

    public static DemoProductCatalogDocument empty() {
        return new DemoProductCatalogDocument(new ArrayList<>(), new ArrayList<>());
    }
}
