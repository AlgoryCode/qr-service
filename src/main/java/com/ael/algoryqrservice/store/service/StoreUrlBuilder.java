package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.store.model.Merchant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StoreUrlBuilder {

    public static final String PATH_PREFIX = "/store/";

    private final AppProperties appProperties;

    public String buildHandle(Merchant merchant) {
        return merchant.getStoreNo() + "-" + merchant.getPublicToken();
    }

    public String buildUrl(Merchant merchant) {
        return trimTrailingSlash(appProperties.getUrl()) + PATH_PREFIX + buildHandle(merchant);
    }

    /**
     * Store number is the prefix before the first dash and the public token is the suffix after
     * the last dash. A leftover business slug in the middle is ignored so older links still resolve.
     */
    public Optional<StoreHandle> parse(String handle) {
        if (handle == null || handle.isBlank()) {
            return Optional.empty();
        }
        String trimmed = handle.trim();
        int firstDash = trimmed.indexOf('-');
        int lastDash = trimmed.lastIndexOf('-');
        if (firstDash <= 0 || lastDash == trimmed.length() - 1) {
            return Optional.empty();
        }
        Long storeNo = parseStoreNo(trimmed.substring(0, firstDash));
        if (storeNo == null) {
            return Optional.empty();
        }
        String token = trimmed.substring(lastDash + 1);
        if (token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new StoreHandle(storeNo, token));
    }

    private Long parseStoreNo(String raw) {
        try {
            long storeNo = Long.parseLong(raw);
            return storeNo > 0 ? storeNo : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
