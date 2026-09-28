package com.ael.algoryqrservice.stage;

import com.ael.algoryqrservice.config.AuthServiceClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AuthMerchantSoftDeleteClient {

    private final RestClient.Builder restClientBuilder;
    private final AuthServiceClientProperties authServiceClientProperties;

    public void softDelete(Long merchantId) {
        String baseUrl = authServiceClientProperties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Auth service URL missing");
        }
        String headerName = authServiceClientProperties.getHeaderName();
        restClientBuilder.build()
                .post()
                .uri(trimTrailingSlash(baseUrl) + "/internal/merchants/" + merchantId + "/soft-delete")
                .header(headerName, authServiceClientProperties.getToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
