package com.ael.algoryqrservice.integration.odeal.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "odeal")
@Slf4j
public class OdealProperties {

    private boolean enabled = true;
    private String baseUrl = "https://stage.odealapp.com/api/v1";
    private String merchantKey = "";
    private String secretKey = "";
    private String externalDeviceKey = "";
    private int defaultVatRatio = 10;
    private String testApiKey = "odeal-test-local";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);

    @PostConstruct
    void logConfigurationStatus() {
        if (!enabled) {
            log.info("Ödeal entegrasyonu devre dışı");
            return;
        }
        if (merchantKey == null || merchantKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("Ödeal API key'leri eksik. application.yml içinde odeal.merchant-key ve odeal.secret-key tanımlayın.");
            return;
        }
        log.info("Ödeal yapılandırıldı baseUrl={} externalDeviceKey={}", baseUrl, mask(externalDeviceKey));
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "(boş)";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
