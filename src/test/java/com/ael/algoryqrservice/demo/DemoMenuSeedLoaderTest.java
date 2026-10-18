package com.ael.algoryqrservice.demo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoMenuSeedLoaderTest {

    @Test
    void loadClasspathSeed_hasCategoriesAndProducts() {
        DemoProperties properties = new DemoProperties();
        properties.getWorkspace().setSeedResource("classpath:seed/demo-algory-menu.json");
        DemoMenuSeedLoader loader = new DemoMenuSeedLoader(
                properties,
                new org.springframework.core.io.DefaultResourceLoader(),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        DemoMenuSeedDtos.Document document = loader.load();

        assertThat(document.getCategories()).isNotEmpty();
        assertThat(document.getProducts()).hasSizeGreaterThanOrEqualTo(10);
        assertThat(document.getProducts().stream().map(DemoMenuSeedDtos.ProductSeed::getPrice))
                .allMatch(price -> price == null || price.compareTo(BigDecimal.ZERO) >= 0);
        assertThat(document.getProducts().stream().map(DemoMenuSeedDtos.ProductSeed::getSubCategorySlug))
                .doesNotContainNull();
    }
}
