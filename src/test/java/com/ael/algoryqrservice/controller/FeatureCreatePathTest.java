package com.ael.algoryqrservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureCreatePathTest {

    @Test
    void createMappings_whenFeatureControllers_thenUseFeaturesPrefix() {
        assertThat(FeatureQrController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/features/QR_MENU");
        assertThat(postPaths(FeatureQrController.class)).containsExactly("/qr/create");

        assertThat(FeatureBranchController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/features/QR_BRANCH");
        assertThat(postPaths(FeatureBranchController.class)).containsExactly("/branches");

        assertThat(FeatureSmartReportingController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/features/SMART_REPORTING");
        assertThat(FeatureAiMenuImportController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/features/AI_MENU_IMPORT");
    }

    @Test
    void legacyCreateMappings_whenOldControllers_thenHaveNoCreatePost() {
        assertThat(postPaths(QrController.class)).isEmpty();
        assertThat(postPaths(BranchController.class)).doesNotContain("", "/");
        assertThat(postPaths(AnalyticsController.class)).doesNotContain(
                "/branch/{branchId}/smart-reports",
                "/menu/{menuId}/smart-reports"
        );
        assertThat(postPaths(AiMenuImportController.class)).isEmpty();
    }

    private static java.util.List<String> postPaths(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .map(FeatureCreatePathTest::mappingValue)
                .toList();
    }

    private static String mappingValue(Method method) {
        String[] values = method.getAnnotation(PostMapping.class).value();
        if (values.length == 0) {
            values = method.getAnnotation(PostMapping.class).path();
        }
        return values.length == 0 ? "" : values[0];
    }
}
