package com.ael.algoryqrservice.demo.provision;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class DemoMenuPresentationFixture {

    private static final String RESOURCE = "demo/demo-menu-presentation.json";

    private final DemoMenuPresentation presentation;

    public DemoMenuPresentationFixture(ObjectMapper objectMapper) {
        this.presentation = load(objectMapper);
    }

    public DemoMenuPresentation get() {
        return presentation;
    }

    private static DemoMenuPresentation load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            DemoMenuPresentation parsed = objectMapper.readValue(input, DemoMenuPresentation.class);
            if (parsed == null) {
                return DemoMenuPresentation.defaults();
            }
            return parsed;
        } catch (Exception exception) {
            return DemoMenuPresentation.defaults();
        }
    }
}
