package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.enums.NutritionBasis;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.model.nutrition.NutritionNutrientEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NutritionFactsServiceTest {

    private NutritionFactsService nutritionFactsService;

    @BeforeEach
    void setUp() {
        nutritionFactsService = new NutritionFactsService(new ObjectMapper());
    }

    @Test
    void validateForCreate_whenMissingRequired_thenThrow() {
        NutritionFacts nutrition = validNutrition();
        nutrition.setSalt(null);

        assertThatThrownBy(() -> nutritionFactsService.validateForCreate(nutrition))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("salt");
    }

    @Test
    void merge_whenOnlySaltProvided_thenPreserveOtherFields() {
        NutritionFacts existing = validNutrition();
        existing.setOtherNutrients(List.of(
                NutritionNutrientEntry.builder().name("Omega-3").value(new BigDecimal("0.5")).unit("g").build()
        ));

        NutritionFacts patch = NutritionFacts.builder()
                .salt(new BigDecimal("1.4"))
                .build();

        NutritionFacts merged = nutritionFactsService.merge(existing, patch);

        assertThat(merged.getSalt()).isEqualByComparingTo("1.4");
        assertThat(merged.getEnergyKcal()).isEqualByComparingTo("203");
        assertThat(merged.getFat()).isEqualByComparingTo("10.5");
        assertThat(merged.getOtherNutrients()).hasSize(1);
        assertThat(merged.getOtherNutrients().getFirst().getName()).isEqualTo("Omega-3");
    }

    @Test
    void merge_whenOnlyOneEnergyProvided_thenThrow() {
        NutritionFacts existing = validNutrition();
        NutritionFacts patch = NutritionFacts.builder()
                .energyKcal(new BigDecimal("220"))
                .build();

        assertThatThrownBy(() -> nutritionFactsService.merge(existing, patch))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("kJ");
    }

    private NutritionFacts validNutrition() {
        return NutritionFacts.builder()
                .basis(NutritionBasis.PER_100G)
                .energyKj(new BigDecimal("850"))
                .energyKcal(new BigDecimal("203"))
                .fat(new BigDecimal("10.5"))
                .carbohydrate(new BigDecimal("25"))
                .fibre(new BigDecimal("2.1"))
                .protein(new BigDecimal("8"))
                .salt(new BigDecimal("1.2"))
                .build();
    }
}
