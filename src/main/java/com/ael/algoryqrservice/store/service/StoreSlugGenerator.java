package com.ael.algoryqrservice.store.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

@Component
public class StoreSlugGenerator {

    private static final int MAX_LENGTH = 80;
    private static final String FALLBACK = "magaza";

    private static final Map<Character, String> TURKISH = Map.of(
            'ı', "i",
            'İ', "i",
            'ş', "s",
            'Ş', "s",
            'ğ', "g",
            'Ğ', "g",
            'ç', "c",
            'Ç', "c",
            'ö', "o",
            'Ö', "o"
    );

    public String generate(String businessName) {
        if (businessName == null || businessName.isBlank()) {
            return FALLBACK;
        }
        StringBuilder transliterated = new StringBuilder(businessName.length());
        for (char character : businessName.toCharArray()) {
            transliterated.append(TURKISH.getOrDefault(character, String.valueOf(character)));
        }
        String slug = Normalizer.normalize(transliterated.toString(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.isBlank()) {
            return FALLBACK;
        }
        return slug.length() > MAX_LENGTH ? trimTrailingDash(slug.substring(0, MAX_LENGTH)) : slug;
    }

    private String trimTrailingDash(String value) {
        return value.replaceAll("-+$", "");
    }
}
