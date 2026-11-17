package com.ael.algoryqrservice.qrservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks at startup that every {@link FeatureKey} the code refers to exists in {@code features}, so
 * a feature that was renamed or never seeded fails the deploy instead of refusing merchants later.
 * {@code app.features.validation}: {@code fail} (default) stops startup, {@code warn} only logs.
 */
@Slf4j
@Component
public class FeatureCatalogValidator {

    private final JdbcTemplate jdbc;
    private final String mode;

    public FeatureCatalogValidator(JdbcTemplate jdbc, @Value("${app.features.validation:fail}") String mode) {
        this.jdbc = jdbc;
        this.mode = mode;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if ("off".equalsIgnoreCase(mode)) {
            return;
        }
        List<String> missing = missing();
        if (missing.isEmpty()) {
            return;
        }
        String message = "Features used in code but missing from the features table: " + missing;
        if ("warn".equalsIgnoreCase(mode)) {
            log.warn(message);
            return;
        }
        throw new IllegalStateException(message);
    }

    List<String> missing() {
        Set<String> known = new HashSet<>(jdbc.queryForList("select feature_key from features", String.class));
        return Arrays.stream(FeatureKey.values()).map(Enum::name).filter(key -> !known.contains(key)).toList();
    }
}
