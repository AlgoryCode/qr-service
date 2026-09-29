package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.model.enums.NutritionBasis;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;

import java.math.BigDecimal;
import java.util.Locale;

/** Demo katalog urunleri icin zorunlu besin degerleri (DB NOT NULL). */
public final class DemoProductCatalogNutritionDefaults {

    private DemoProductCatalogNutritionDefaults() {
    }

    public static NutritionFacts resolve(String nutritionProfile, String subCategorySlug) {
        String profile = nutritionProfile == null || nutritionProfile.isBlank()
                ? inferProfile(subCategorySlug)
                : nutritionProfile.trim().toLowerCase(Locale.ROOT);
        return switch (profile) {
            case "dessert", "tatli" -> dessertPer100g();
            case "drink_hot", "sicak_icecek", "hot" -> hotDrinkPer100ml();
            case "drink_cold", "soguk_icecek", "cold" -> coldDrinkPer100ml();
            default -> mainCoursePer100g();
        };
    }

    private static String inferProfile(String subCategorySlug) {
        if (subCategorySlug == null) {
            return "main_course";
        }
        String slug = subCategorySlug.toLowerCase(Locale.ROOT);
        if (slug.contains("tatli")) {
            return "dessert";
        }
        if (slug.contains("sicak")) {
            return "drink_hot";
        }
        if (slug.contains("soguk")) {
            return "drink_cold";
        }
        return "main_course";
    }

    public static NutritionFacts mainCoursePer100g() {
        return NutritionFacts.builder()
                .basis(NutritionBasis.PER_100G)
                .energyKj(new BigDecimal("890"))
                .energyKcal(new BigDecimal("213"))
                .fat(new BigDecimal("12.5"))
                .saturatedFat(new BigDecimal("4.8"))
                .carbohydrate(new BigDecimal("6.2"))
                .sugars(new BigDecimal("1.1"))
                .fibre(new BigDecimal("0.8"))
                .protein(new BigDecimal("18.4"))
                .salt(new BigDecimal("1.2"))
                .build();
    }

    public static NutritionFacts dessertPer100g() {
        return NutritionFacts.builder()
                .basis(NutritionBasis.PER_100G)
                .energyKj(new BigDecimal("1450"))
                .energyKcal(new BigDecimal("347"))
                .fat(new BigDecimal("16.2"))
                .saturatedFat(new BigDecimal("9.5"))
                .carbohydrate(new BigDecimal("42.0"))
                .sugars(new BigDecimal("28.5"))
                .fibre(new BigDecimal("1.2"))
                .protein(new BigDecimal("5.8"))
                .salt(new BigDecimal("0.35"))
                .build();
    }

    public static NutritionFacts hotDrinkPer100ml() {
        return NutritionFacts.builder()
                .basis(NutritionBasis.PER_100ML)
                .energyKj(new BigDecimal("8"))
                .energyKcal(new BigDecimal("2"))
                .fat(new BigDecimal("0.1"))
                .saturatedFat(new BigDecimal("0.0"))
                .carbohydrate(new BigDecimal("0.3"))
                .sugars(new BigDecimal("0.0"))
                .fibre(new BigDecimal("0.0"))
                .protein(new BigDecimal("0.2"))
                .salt(new BigDecimal("0.0"))
                .build();
    }

    public static NutritionFacts coldDrinkPer100ml() {
        return NutritionFacts.builder()
                .basis(NutritionBasis.PER_100ML)
                .energyKj(new BigDecimal("180"))
                .energyKcal(new BigDecimal("43"))
                .fat(new BigDecimal("0.0"))
                .saturatedFat(new BigDecimal("0.0"))
                .carbohydrate(new BigDecimal("10.5"))
                .sugars(new BigDecimal("9.8"))
                .fibre(new BigDecimal("0.1"))
                .protein(new BigDecimal("0.2"))
                .salt(new BigDecimal("0.05"))
                .build();
    }
}
