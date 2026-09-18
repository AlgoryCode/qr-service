package com.ael.algoryqrservice.store.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSlugGeneratorTest {

    private final StoreSlugGenerator storeSlugGenerator = new StoreSlugGenerator();

    @Test
    void generate_whenTurkishCharacters_thenTransliterate() {
        assertThat(storeSlugGenerator.generate("Kebapçı Mehmet'in Yeri")).isEqualTo("kebapci-mehmet-in-yeri");
        assertThat(storeSlugGenerator.generate("Şığır Döner")).isEqualTo("sigir-doner");
    }

    @Test
    void generate_whenBlank_thenFallback() {
        assertThat(storeSlugGenerator.generate(null)).isEqualTo("magaza");
        assertThat(storeSlugGenerator.generate("   ")).isEqualTo("magaza");
        assertThat(storeSlugGenerator.generate("!!!")).isEqualTo("magaza");
    }

    @Test
    void generate_whenVeryLong_thenTruncateWithoutTrailingDash() {
        String slug = storeSlugGenerator.generate("a".repeat(120));

        assertThat(slug).hasSize(80).doesNotEndWith("-");
    }
}
