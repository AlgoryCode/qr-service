package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.smart-report")
public class SmartReportQuotaProperties {

    public enum QuotaPeriod {
        DAY,
        WEEK
    }

    private QuotaPeriod quotaPeriod = QuotaPeriod.DAY;
    private int quotaLimit = 1;
    private String zone = "Europe/Istanbul";
}

