package com.ael.algoryqrservice.catalog;

import com.ael.algoryqrservice.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

@Component
public class CatalogCodeFactory {

    public String fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String asciiFriendly = name.trim()
                .replace('\u0131', 'i')
                .replace('\u0130', 'I')
                .replace('\u011f', 'g')
                .replace('\u011e', 'G')
                .replace('\u00fc', 'u')
                .replace('\u00dc', 'U')
                .replace('\u015f', 's')
                .replace('\u015e', 'S')
                .replace('\u00f6', 'o')
                .replace('\u00d6', 'O')
                .replace('\u00e7', 'c')
                .replace('\u00c7', 'C');
        String normalized = Normalizer.normalize(asciiFriendly, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
        if (normalized.isBlank()) {
            return null;
        }
        if (!Character.isLetter(normalized.charAt(0))) {
            normalized = "P_" + normalized;
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64).replaceAll("_+$", "");
        }
        return normalized;
    }

    public String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public String resolveUnique(String requestedCode, String name, Predicate<String> exists) {
        String base = requestedCode != null && !requestedCode.isBlank()
                ? normalize(requestedCode)
                : fromName(name);
        if (base == null || base.isBlank()) {
            throw new BadRequestException("Kod uretilemedi; ad gecersiz");
        }
        if (!exists.test(base)) {
            return base;
        }
        for (int i = 2; i <= 99; i++) {
            String candidate = base + "_" + i;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new BadRequestException("Bu kod zaten mevcut: " + base);
    }
}
