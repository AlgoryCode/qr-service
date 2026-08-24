package com.ael.algoryqrservice.integration.odeal.client;

import com.ael.algoryqrservice.integration.odeal.config.OdealProperties;
import com.ael.algoryqrservice.integration.odeal.model.dto.OdealTestDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
@Slf4j
public class OdealClient {

    private final RestClient restClient;
    private final OdealProperties properties;
    private final ObjectMapper objectMapper;

    public OdealClient(
            @Qualifier("odealRestClient") RestClient restClient,
            OdealProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public OdealTestDtos.ProxyResponse getUnits() {
        return exchange("GET", "/units", null);
    }

    public OdealTestDtos.ProxyResponse sendBasket(JsonNode body) {
        return exchange("POST", "/basket", body);
    }

    private OdealTestDtos.ProxyResponse exchange(String method, String path, JsonNode body) {
        ensureCredentialsConfigured();
        try {
            RestClient.RequestBodySpec request = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(path)
                    .headers(this::applyAuthHeaders);

            ResponseEntity<String> response;
            if (body != null) {
                response = request
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(body))
                        .retrieve()
                        .toEntity(String.class);
            } else {
                response = request.retrieve().toEntity(String.class);
            }

            return toProxyResponse(response.getStatusCode().value(), response.getBody());
        } catch (RestClientResponseException exception) {
            log.warn("Odeal API error path={} status={} body={}", path, exception.getStatusCode().value(), exception.getResponseBodyAsString());
            return toProxyResponse(exception.getStatusCode().value(), exception.getResponseBodyAsString());
        } catch (Exception exception) {
            throw new OdealClientException("Odeal API isteği başarısız: " + path, exception);
        }
    }

    private void applyAuthHeaders(HttpHeaders headers) {
        headers.set("X-ODEAL-MERCHANT-KEY", properties.getMerchantKey());
        headers.set("X-ODEAL-SECRET-KEY", properties.getSecretKey());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    }

    private void ensureCredentialsConfigured() {
        if (properties.getMerchantKey() == null || properties.getMerchantKey().isBlank()) {
            throw new OdealClientException("ODEAL_MERCHANT_KEY yapılandırılmamış");
        }
        if (properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            throw new OdealClientException("ODEAL_SECRET_KEY yapılandırılmamış");
        }
    }

    private OdealTestDtos.ProxyResponse toProxyResponse(int statusCode, String rawBody) {
        Object parsedBody = null;
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonNode jsonNode = objectMapper.readTree(rawBody);
                parsedBody = objectMapper.convertValue(jsonNode, Object.class);
            } catch (Exception exception) {
                log.debug("Odeal response JSON parse edilemedi", exception);
            }
        }
        return OdealTestDtos.ProxyResponse.builder()
                .statusCode(statusCode)
                .body(parsedBody)
                .rawBody(rawBody)
                .build();
    }
}
