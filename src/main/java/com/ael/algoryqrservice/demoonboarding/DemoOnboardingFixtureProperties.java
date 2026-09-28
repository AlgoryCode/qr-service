package com.ael.algoryqrservice.demoonboarding;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Prod şablonları: katalog + satış kaynağı (Aya Roof), görünüm/tema (İlhan Aytuğ menüsü).
 * ID'ler ortam değişkenleriyle verilir; 0 ise fixture atlanır.
 */
@Component
@ConfigurationProperties(prefix = "app.demo-onboarding-fixture")
@Getter
@Setter
public class DemoOnboardingFixtureProperties {

    private boolean enabled = true;
    /** Aya Roof — ürün katalog şablonu kullanıcı id */
    private Long catalogTemplateUserId = 0L;
    private Long catalogTemplateBranchId = 0L;
    private Long catalogTemplateMenuId = 0L;
    /** İlhan Aytuğ — tema ve menü görünümü şablonu */
    private Long themeTemplateUserId = 0L;
    private Long themeTemplateMenuId = 0L;
    private int salesBackfillDays = 35;
    private int salesMaxBills = 120;

    public boolean isReady() {
        return enabled
                && positive(catalogTemplateUserId)
                && positive(catalogTemplateBranchId)
                && positive(catalogTemplateMenuId)
                && positive(themeTemplateUserId)
                && positive(themeTemplateMenuId);
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }
}
