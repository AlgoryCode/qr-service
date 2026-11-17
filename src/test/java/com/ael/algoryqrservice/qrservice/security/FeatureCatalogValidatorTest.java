package com.ael.algoryqrservice.qrservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureCatalogValidatorTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void missingFeatureStopsStartup() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("QR_MENU"));

        assertThatThrownBy(() -> new FeatureCatalogValidator(jdbc, "fail").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMART_REPORTING");
    }

    @Test
    void warnModeOnlyLogs() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        assertThatCode(() -> new FeatureCatalogValidator(jdbc, "warn").validate()).doesNotThrowAnyException();
    }

    @Test
    void completeCatalogPasses() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(Arrays.stream(FeatureKey.values()).map(Enum::name).toList());

        assertThat(new FeatureCatalogValidator(jdbc, "fail").missing()).isEmpty();
    }

    /** The migrations seed every key the code uses, so a fresh database passes the startup check. */
    @Test
    void migrationsSeedEveryFeatureKey() throws Exception {
        String sql;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            sql = files.filter(path -> path.getFileName().toString().matches("V(19[0-9]|2\\d\\d)__.*\\.sql"))
                    .map(FeatureCatalogValidatorTest::read)
                    .collect(Collectors.joining("\n"));
        }
        Matcher seeded = Pattern.compile("\\('([A-Z_]+)',").matcher(sql);
        List<String> keys = seeded.results().map(match -> match.group(1)).toList();

        assertThat(keys).containsAll(Arrays.stream(FeatureKey.values()).map(Enum::name).toList());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
