package com.ael.algoryqrservice.demo.provision;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class DemoProductCatalogFixture {

    private static final String RESOURCE = "demo/demo-product-catalog.json";

    private final DemoProductCatalogDocument document;

    public DemoProductCatalogFixture(ObjectMapper objectMapper) {
        this.document = load(objectMapper);
    }

    public DemoProductCatalogDocument get() {
        return document;
    }

    private static DemoProductCatalogDocument load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            DemoProductCatalogDocument parsed = objectMapper.readValue(input, DemoProductCatalogDocument.class);
            if (parsed == null) {
                return DemoProductCatalogDocument.empty();
            }
            return parsed;
        } catch (Exception exception) {
            return DemoProductCatalogDocument.empty();
        }
    }
}
