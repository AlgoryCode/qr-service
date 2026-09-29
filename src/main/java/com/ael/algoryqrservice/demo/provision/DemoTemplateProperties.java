package com.ael.algoryqrservice.demo.provision;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Demo mağaza provision ayarları. Vitrin/tema: {@code demo/demo-menu-presentation.json};
 * ürün kataloğu: {@code demo/demo-product-catalog.json} (dış kaynak menü gerekmez).
 */
@Component
@ConfigurationProperties(prefix = "app.demo-template")
@Getter
@Setter
public class DemoTemplateProperties {

    private boolean enabled = true;
    private int salesBackfillDays = 35;
    private int salesMaxBills = 120;

    public boolean isReady() {
        return enabled;
    }
}
