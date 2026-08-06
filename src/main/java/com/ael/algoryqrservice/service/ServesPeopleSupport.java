package com.ael.algoryqrservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ServesPeopleSupport {

    private static final Pattern EXPLICIT_COUNT = Pattern.compile(
            "\\((\\d+)\\s*ki[sş]ilik\\)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public record Range(Integer min, Integer max) {
    }

    public Range normalize(Integer min, Integer max) {
        if (min == null && max == null) {
            return new Range(null, null);
        }
        if (min == null || max == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "servesPeopleMin ve servesPeopleMax birlikte verilmelidir"
            );
        }
        if (min < 1 || max < min) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Geçersiz kişi sayısı aralığı"
            );
        }
        return new Range(min, max);
    }

    public Range resolveFromSeed(Integer min, Integer max, String name) {
        if (min != null || max != null) {
            return normalize(min, max);
        }
        Range inferred = inferFromName(name);
        if (inferred != null) {
            return inferred;
        }
        return new Range(1, 1);
    }

    public Range inferFromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains("tek kişilik") || normalized.contains("tek kisilik")) {
            return new Range(1, 1);
        }
        Matcher matcher = EXPLICIT_COUNT.matcher(name);
        if (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            if (count >= 1) {
                return new Range(count, count);
            }
        }
        return null;
    }

    public double midpoint(Integer min, Integer max) {
        if (min == null || max == null) {
            return 1.0d;
        }
        return (min + max) / 2.0d;
    }

    public boolean overlapsBucket(Integer min, Integer max, int bucketMin, Integer bucketMaxInclusive) {
        if (min == null || max == null) {
            return false;
        }
        int high = bucketMaxInclusive == null ? Integer.MAX_VALUE : bucketMaxInclusive;
        return min <= high && max >= bucketMin;
    }
}
