package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.config.AuthServiceClientProperties;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class AuthMerchantClient {

    private static final String MERCHANT_PATH = "/internal/merchants/{merchantId}";

    private final RestClient.Builder restClientBuilder;
    private final AuthServiceClientProperties properties;

    public MerchantView findMerchant(Long merchantId) {
        if (!properties.isEnabled() || merchantId == null) {
            throw new FulfillmentUnavailableException("Hesap servisi yapilandirilmamis");
        }
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new FulfillmentUnavailableException("Hesap servisi yapilandirilmamis");
        }
        try {
            MerchantView view = restClientBuilder.build()
                    .get()
                    .uri(trimSlash(baseUrl) + MERCHANT_PATH, merchantId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(MerchantView.class);
            if (view == null || view.id() == null) {
                throw new FulfillmentUnavailableException("Hesap servisi gecersiz yanit dondu");
            }
            return view;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new NotFoundException("Merchant bulunamadi");
            }
            throw new FulfillmentUnavailableException("Hesap servisi kullanilamiyor");
        } catch (RestClientException exception) {
            throw new FulfillmentUnavailableException("Hesap servisi kullanilamiyor");
        }
    }

    private static String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MerchantView(Long id, String status) {
    }
}
