package com.ael.algoryqrservice.util;

import java.util.Locale;
import java.util.Optional;

/**
 * Null-safe enum parsing so that unknown external values never escape as
 * {@link IllegalArgumentException} from webhooks, query parameters or legacy columns.
 */
public final class Enums {

    private Enums() {
    }

    public static <TEnum extends Enum<TEnum>> Optional<TEnum> parse(Class<TEnum> type, String value) {
        String normalized = TextUtils.trimToNull(value);
        if (normalized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, normalized.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknownConstant) {
            return Optional.empty();
        }
    }

    public static <TEnum extends Enum<TEnum>> TEnum parseOrDefault(Class<TEnum> type, String value, TEnum fallback) {
        return parse(type, value).orElse(fallback);
    }
}
