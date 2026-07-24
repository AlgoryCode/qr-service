package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "billing.refund")
public class BillingRefundProperties {

    private int monthlyCoolingDays = 7;
    private int yearlyCoolingDays = 14;
}
