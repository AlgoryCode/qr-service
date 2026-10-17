package com.ael.algoryqrservice.qrservice.config;

import com.ael.algoryqrservice.qrservice.util.AppTime;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationTimezoneConfig {

    @PostConstruct
    void configureApplicationTimeZone() {
        AppTime.initializeDefaultTimeZone();
    }
}
