package com.ael.algoryqrservice.catalog;

import java.util.Locale;
import java.util.Set;

public final class CatalogThemes {

    public static final Set<String> STANDARD_THEME_IDS = Set.of("soft", "classic");

    private CatalogThemes() {
    }

    public static boolean isCustomTheme(String themeId) {
        if (themeId == null || themeId.isBlank()) {
            return false;
        }
        return !STANDARD_THEME_IDS.contains(themeId.trim().toLowerCase(Locale.ROOT));
    }
}
