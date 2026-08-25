package com.ael.algoryqrservice.util;

/**
 * Shared text normalisation helpers.
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * @return the trimmed value, or {@code null} when the input is null, empty or whitespace only.
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * @return the trimmed value, or {@code fallback} when the input holds no text.
     */
    public static String trimToDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    /**
     * @return {@code true} when the value is null, empty or whitespace only.
     */
    public static boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    /**
     * @return {@code true} when the value holds at least one non-whitespace character.
     */
    public static boolean hasText(String value) {
        return !isBlank(value);
    }
}
