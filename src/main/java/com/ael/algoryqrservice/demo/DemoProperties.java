package com.ael.algoryqrservice.demo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.demo")
@Getter
@Setter
public class DemoProperties {

    private boolean enabled;
    private String email = "";
    private String password = "";

    public boolean isConfigured() {
        return enabled && hasText(email) && hasText(password);
    }

    public String normalizedEmail() {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
