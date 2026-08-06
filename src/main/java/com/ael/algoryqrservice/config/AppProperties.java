package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String url = "http://localhost:3000";
    private String serviceName = "qr-service";
    private MenuSettings menu = new MenuSettings();
    private SeedSettings seed = new SeedSettings();

    @Getter
    @Setter
    public static class MenuSettings {
        private int categoryMaxDepth = 10;
    }

    @Getter
    @Setter
    public static class SeedSettings {
        private MenuProductsSeed menuProducts = new MenuProductsSeed();
    }

    @Getter
    @Setter
    public static class MenuProductsSeed {
        private boolean enabled = false;
        private boolean onlyIfEmpty = false;
    }
}
