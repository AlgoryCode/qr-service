package com.ael.algoryqrservice.integration.ubereats.client;

import com.ael.algoryqrservice.integration.ubereats.config.UberEatsProperties;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class UberEatsClient {

    private final RestClient apiClient;
    private final RestClient authClient;
    private final UberEatsProperties properties;
    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public UberEatsClient(
            @Qualifier("uberEatsRestClient") RestClient apiClient,
            @Qualifier("uberEatsAuthRestClient") RestClient authClient,
            UberEatsProperties properties
    ) {
        this.apiClient = apiClient;
        this.authClient = authClient;
        this.properties = properties;
    }

    public JsonNode getMenu(UberEatsDtos.Credentials credentials) {
        return get("/v2/eats/stores/" + credentials.storeId() + "/menus", credentials);
    }

    public void uploadMenu(UberEatsDtos.Credentials credentials, JsonNode menuPayload) {
        put("/v2/eats/stores/" + credentials.storeId() + "/menus", credentials, menuPayload);
    }

    public void updateItem(UberEatsDtos.Credentials credentials, String itemId, JsonNode itemPayload) {
        post(
                "/v2/eats/stores/" + credentials.storeId() + "/menus/items/" + itemId,
                credentials,
                itemPayload
        );
    }

    private JsonNode get(String path, UberEatsDtos.Credentials credentials) {
        return exchange("GET", path, credentials, null);
    }

    private void put(String path, UberEatsDtos.Credentials credentials, JsonNode body) {
        exchange("PUT", path, credentials, body);
    }

    private void post(String path, UberEatsDtos.Credentials credentials, JsonNode body) {
        exchange("POST", path, credentials, body);
    }

    private JsonNode exchange(String method, String path, UberEatsDtos.Credentials credentials, JsonNode body) {
        int attempts = Math.max(1, properties.getMaxAttempts());
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String token = accessToken(credentials);
                if ("GET".equals(method)) {
                    return apiClient.get()
                            .uri(path)
                            .headers(headers -> applyAuth(headers, token))
                            .retrieve()
                            .body(JsonNode.class);
                }
                if ("PUT".equals(method)) {
                    apiClient.put()
                            .uri(path)
                            .headers(headers -> applyAuth(headers, token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                }
                if ("POST".equals(method)) {
                    apiClient.post()
                            .uri(path)
                            .headers(headers -> applyAuth(headers, token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                }
                throw new IllegalArgumentException(method);
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                invalidateToken(credentials);
                lastError = new UberEatsClientException(
                        "Uber Eats isteği başarısız oldu: HTTP " + status,
                        status,
                        retryable,
                        exception
                );
                if (!retryable || attempt == attempts) {
                    throw lastError;
                }
                sleepBackoff(attempt);
            } catch (RuntimeException exception) {
                if (exception instanceof UberEatsClientException clientException) {
                    throw clientException;
                }
                lastError = new UberEatsClientException(
                        "Uber Eats yanıt vermedi",
                        0,
                        true,
                        exception
                );
                if (attempt == attempts) {
                    throw lastError;
                }
                sleepBackoff(attempt);
            }
        }
        throw lastError == null
                ? new UberEatsClientException("Uber Eats isteği başarısız oldu", 0, true)
                : lastError;
    }

    private String accessToken(UberEatsDtos.Credentials credentials) {
        String cacheKey = credentials.clientId() + "|" + credentials.storeId();
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return cached.token();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", credentials.clientId());
        form.add("client_secret", credentials.clientSecret());
        form.add("grant_type", "client_credentials");
        form.add("scope", properties.getDefaultScope());
        JsonNode response;
        try {
            response = authClient.post()
                    .uri(properties.getAuthUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new UberEatsClientException(
                    "Uber Eats OAuth başarısız: HTTP " + exception.getStatusCode().value(),
                    exception.getStatusCode().value(),
                    exception.getStatusCode().value() >= 500,
                    exception
            );
        }
        if (response == null || !response.hasNonNull("access_token")) {
            throw new UberEatsClientException("Uber Eats OAuth access_token dönmedi", 0, false);
        }
        String token = response.get("access_token").asText();
        long expiresIn = response.path("expires_in").asLong(3600);
        tokenCache.put(cacheKey, new CachedToken(token, Instant.now().plusSeconds(Math.max(60, expiresIn))));
        return token;
    }

    private void invalidateToken(UberEatsDtos.Credentials credentials) {
        tokenCache.remove(credentials.clientId() + "|" + credentials.storeId());
    }

    private void applyAuth(HttpHeaders headers, String token) {
        headers.setBearerAuth(token);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(2000L * attempt, 8000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
