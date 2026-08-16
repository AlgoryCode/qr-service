package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.util.AppTime;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationTimezoneConfig {

    @PostConstruct
    void configureApplicationTimeZone() {
        AppTime.initializeDefaultTimeZone();
    }
}
