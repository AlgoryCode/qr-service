package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.model.enums.NutritionBasis;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.service.NutritionFactsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DemoProductCatalogNutritionDefaultsTest {

    private final NutritionFactsService nutritionFactsService = new NutritionFactsService(new ObjectMapper());

    @Test
    void allProfiles_passCreateValidation() {
        assertValid(DemoProductCatalogNutritionDefaults.resolve("main_course", "kofte_izgara"));
        assertValid(DemoProductCatalogNutritionDefaults.resolve("dessert", "klasik_tatli"));
        assertValid(DemoProductCatalogNutritionDefaults.resolve("drink_hot", "sicak_icecek"));
        assertValid(DemoProductCatalogNutritionDefaults.resolve("drink_cold", "soguk_icecek"));
        assertValid(DemoProductCatalogNutritionDefaults.resolve(null, "kofte_izgara"));
    }

    @Test
    void mainCourse_usesPer100g() {
        NutritionFacts facts = DemoProductCatalogNutritionDefaults.mainCoursePer100g();
        assertEquals(NutritionBasis.PER_100G, facts.getBasis());
        assertNotNull(facts.getProtein());
    }

    private void assertValid(NutritionFacts facts) {
        assertNotNull(facts);
        nutritionFactsService.validateForCreate(facts);
    }
}
