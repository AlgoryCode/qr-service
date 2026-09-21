package com.ael.algoryqrservice.integration.yemeksepeti.client;

import com.ael.algoryqrservice.integration.yemeksepeti.config.YemekSepetiProperties;
import com.ael.algoryqrservice.integration.yemeksepeti.mapper.YemekSepetiPayloadMapper;
import com.ael.algoryqrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class YemekSepetiClient {

    private final RestClient restClient;
    private final YemekSepetiProperties properties;
    private final YemekSepetiPayloadMapper payloadMapper;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public YemekSepetiClient(
            @Qualifier("yemekSepetiRestClient") RestClient restClient,
            YemekSepetiProperties properties,
            YemekSepetiPayloadMapper payloadMapper,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.payloadMapper = payloadMapper;
        this.objectMapper = objectMapper;
    }

    public List<JsonNode> listAllOrders(YemekSepetiDtos.Credentials credentials, Instant start, Instant end) {
        String path = UriComponentsBuilder.fromPath(expand(properties.getVendorOrdersPath(), credentials, null))
                .queryParam("from", start.toString())
                .queryParam("to", end.toString())
                .build(true)
                .toUriString();
        JsonNode payload = exchange(HttpMethod.GET, path, credentials, null);
        return payloadMapper.toOrderNodes(payload);
    }

    public void acceptOrder(YemekSepetiDtos.Credentials credentials, String orderId) {
        putOrder(credentials, orderId, "RECEIVED");
    }

    public void rejectOrder(YemekSepetiDtos.Credentials credentials, String orderId) {
        putOrder(credentials, orderId, "CANCELLED");
    }

    public void cancelOrder(YemekSepetiDtos.Credentials credentials, String orderId) {
        putOrder(credentials, orderId, "CANCELLED");
    }

    public void markReady(YemekSepetiDtos.Credentials credentials, String orderId) {
        putOrder(credentials, orderId, "READY_FOR_PICKUP");
    }

    private void putOrder(YemekSepetiDtos.Credentials credentials, String orderId, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", orderId);
        body.put("status", status);
        body.put("items", List.of());
        exchange(HttpMethod.PUT, expand(properties.getOrderPath(), credentials, orderId), credentials, body);
    }

    private JsonNode exchange(HttpMethod method, String path, YemekSepetiDtos.Credentials credentials, Object body) {
        int attempts = Math.max(1, properties.getMaxAttempts());
        RestClientResponseException lastResponse = null;
        Exception lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                var spec = restClient.method(method)
                        .uri(path)
                        .header("Authorization", "Bearer " + accessToken(credentials))
                        .accept(MediaType.APPLICATION_JSON);
                if (body != null) {
                    spec.contentType(MediaType.APPLICATION_JSON).body(body);
                }
                String raw = spec.retrieve().body(String.class);
                if (raw == null || raw.isBlank()) {
                    return objectMapper.createObjectNode();
                }
                return objectMapper.readTree(raw);
            } catch (RestClientResponseException exception) {
                lastResponse = exception;
                if (exception.getStatusCode().value() == 401) {
                    tokens.remove(credentials.getClientId());
                }
                if (exception.getStatusCode().is4xxClientError() && exception.getStatusCode().value() != 429) {
                    break;
                }
            } catch (Exception exception) {
                lastError = exception;
            }
        }
        if (lastResponse != null) {
            throw new YemekSepetiClientException(
                    "Yemeksepeti isteği başarısız",
                    lastResponse,
                    lastResponse.getStatusCode().value()
            );
        }
        throw new YemekSepetiClientException("Yemeksepeti isteği başarısız", lastError);
    }

    private String accessToken(YemekSepetiDtos.Credentials credentials) {
        CachedToken cached = tokens.get(credentials.getClientId());
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return cached.token();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", credentials.getClientId());
        form.add("client_secret", credentials.getClientSecret());
        try {
            JsonNode payload = restClient.post()
                    .uri(properties.getTokenPath())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (payload == null || !payload.hasNonNull("access_token")) {
                throw new YemekSepetiClientException("Yemeksepeti erişim jetonu alınamadı");
            }
            long expiresIn = payload.path("expires_in").asLong(7200);
            String token = payload.get("access_token").asText();
            tokens.put(credentials.getClientId(), new CachedToken(token, Instant.now().plusSeconds(expiresIn)));
            return token;
        } catch (RestClientResponseException exception) {
            throw new YemekSepetiClientException(
                    "Yemeksepeti erişim jetonu alınamadı",
                    exception,
                    exception.getStatusCode().value()
            );
        }
    }

    private String expand(String template, YemekSepetiDtos.Credentials credentials, String orderId) {
        String path = template
                .replace("{chainId}", credentials.getChainId())
                .replace("{chain_id}", credentials.getChainId())
                .replace("{vendorId}", nullToEmpty(credentials.getVendorId()))
                .replace("{vendor_id}", nullToEmpty(credentials.getVendorId()));
        if (orderId != null) {
            path = path.replace("{orderId}", orderId).replace("{order_id}", orderId);
        }
        return path;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
