package com.ael.algoryqrservice.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogThemesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "luxury",
            "elixir",
            "tech-gourmet",
            "soft",
            "classic",
            "petite-patisserie",
            "folio-rouge",
            "lucite-gris",
            "rubric",
            "bigarade",
            "modern-bistro",
            "clever-dish-scribe"
    })
    void isCustomTheme_whenPresetTheme_thenFalse(String themeId) {
        assertThat(CatalogThemes.isCustomTheme(themeId)).isFalse();
        assertThat(CatalogThemes.isPresetTheme(themeId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "custom-brand-cafe",
            "CUSTOM-ai-theme",
            " custom-generated "
    })
    void isCustomTheme_whenAiGeneratedTheme_thenTrue(String themeId) {
        assertThat(CatalogThemes.isCustomTheme(themeId)).isTrue();
        assertThat(CatalogThemes.isPresetTheme(themeId)).isFalse();
    }

    @Test
    void isCustomTheme_whenBlank_thenFalse() {
        assertThat(CatalogThemes.isCustomTheme(null)).isFalse();
        assertThat(CatalogThemes.isCustomTheme("")).isFalse();
        assertThat(CatalogThemes.isCustomTheme("   ")).isFalse();
    }
}
