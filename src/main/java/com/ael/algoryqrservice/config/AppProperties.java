package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.enums.FulfillmentGateMode;
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
    private CorsSettings cors = new CorsSettings();
    private MenuSettings menu = new MenuSettings();
    private SeedSettings seed = new SeedSettings();
    private FulfillmentSettings fulfillment = new FulfillmentSettings();

    @Getter
    @Setter
    public static class CorsSettings {
        private String allowedOrigins = "http://localhost:3000,http://localhost:3001";
    }

    @Getter
    @Setter
    public static class MenuSettings {
        private int categoryMaxDepth = 10;
    }

    @Getter
    @Setter
    public static class SeedSettings {
        private boolean taxonomy = false;
        private boolean packageCatalog = false;
        private boolean publicAccessBackfill = false;
        private boolean branchBackfill = true;
        private boolean waiterBranchBackfill = true;
        private boolean chefAvatars = false;
        private boolean fulfillmentEntitlementBackfill = true;
        private MenuProductsSeed menuProducts = new MenuProductsSeed();
    }

    @Getter
    @Setter
    public static class MenuProductsSeed {
        private boolean enabled = false;
        private boolean onlyIfEmpty = true;
    }

    @Getter
    @Setter
    public static class FulfillmentSettings {
        private FulfillmentGateMode gateMode = FulfillmentGateMode.FULFILLMENT_ONLY;
        private boolean repairJobEnabled = true;
        private int repairJobBatchSize = 100;
    }
}
