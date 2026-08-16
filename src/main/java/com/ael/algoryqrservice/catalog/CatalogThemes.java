package com.ael.algoryqrservice.catalog;

import java.util.Locale;
import java.util.Set;

public final class CatalogThemes {

    /** Hazır menü şablonları — paket kısıtı olmadan seçilebilir. */
    public static final Set<String> PRESET_THEME_IDS = Set.of(
            "soft",
            "classic",
            "luxury",
            "petite-patisserie",
            "folio-rouge",
            "lucite-gris",
            "rubric",
            "bigarade",
            "elixir",
            "tech-gourmet",
            "modern-bistro",
            "clever-dish-scribe"
    );

    /** Yapay zeka ile üretilen özel temalar bu önek ile gelir. */
    public static final String CUSTOM_THEME_PREFIX = "custom-";

    private CatalogThemes() {
    }

    public static boolean isCustomTheme(String themeId) {
        if (themeId == null || themeId.isBlank()) {
            return false;
        }
        return themeId.trim().toLowerCase(Locale.ROOT).startsWith(CUSTOM_THEME_PREFIX);
    }

    public static boolean isPresetTheme(String themeId) {
        if (themeId == null || themeId.isBlank()) {
            return false;
        }
        return PRESET_THEME_IDS.contains(themeId.trim().toLowerCase(Locale.ROOT));
    }
}
