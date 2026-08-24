package com.ael.algoryqrservice.integration.trendyolgo.client;

import com.ael.algoryqrservice.integration.trendyolgo.config.TrendyolGoProperties;
import com.ael.algoryqrservice.integration.trendyolgo.model.dto.TrendyolGoDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TrendyolGoClient {

    private final RestClient restClient;
    private final TrendyolGoProperties properties;
    private final ObjectMapper objectMapper;

    public TrendyolGoClient(
            @Qualifier("trendyolGoRestClient") RestClient restClient,
            TrendyolGoProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JsonNode listRestaurants(TrendyolGoDtos.Credentials credentials) {
        return exchange(HttpMethod.GET, expand(properties.getPaths().getRestaurants(), credentials, null), credentials, null);
    }

    public JsonNode getMenu(TrendyolGoDtos.Credentials credentials) {
        requireRestaurant(credentials);
        return exchange(HttpMethod.GET, expand(properties.getPaths().getRestaurantMenu(), credentials, null), credentials, null);
    }

    public JsonNode listOrders(TrendyolGoDtos.Credentials credentials, Instant start, Instant end) {
        return listOrdersPage(credentials, start, end, 0, properties.getPollPageSize());
    }

    public List<JsonNode> listAllOrders(TrendyolGoDtos.Credentials credentials, Instant start, Instant end) {
        int pageSize = Math.max(1, Math.min(properties.getPollPageSize(), 200));
        List<JsonNode> orders = new ArrayList<>();
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            JsonNode payload = listOrdersPage(credentials, start, end, page, pageSize);
            List<JsonNode> nodes = new ArrayList<>();
            for (JsonNode node : extractOrderNodes(payload)) {
                nodes.add(node);
            }
            orders.addAll(nodes);
            totalPages = Math.max(1, readTotalPages(payload, nodes.isEmpty()));
            page++;
            if (nodes.isEmpty()) {
                break;
            }
        }
        return orders;
    }

    private JsonNode listOrdersPage(
            TrendyolGoDtos.Credentials credentials,
            Instant start,
            Instant end,
            int page,
            int size
    ) {
        String path = expand(properties.getPaths().getOrders(), credentials, null);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path)
                .queryParam("startDate", start.toEpochMilli())
                .queryParam("endDate", end.toEpochMilli())
                .queryParam("page", Math.max(0, page))
                .queryParam("size", Math.max(1, size));
        if (credentials.getRestaurantId() != null && !credentials.getRestaurantId().isBlank()) {
            builder.queryParam("restaurantId", credentials.getRestaurantId());
            builder.queryParam("storeId", credentials.getRestaurantId());
        }
        return exchange(HttpMethod.GET, builder.build(true).toUriString(), credentials, null);
    }

    private Iterable<JsonNode> extractOrderNodes(JsonNode payload) {
        List<JsonNode> nodes = new ArrayList<>();
        if (payload == null || payload.isNull()) {
            return nodes;
        }
        if (payload.isArray()) {
            payload.forEach(nodes::add);
            return nodes;
        }
        JsonNode content = payload.get("content");
        if (content != null && content.isArray()) {
            content.forEach(nodes::add);
            return nodes;
        }
        JsonNode packages = payload.get("packages");
        if (packages != null && packages.isArray()) {
            packages.forEach(nodes::add);
            return nodes;
        }
        if (payload.has("id")) {
            nodes.add(payload);
        }
        return nodes;
    }

    private int readTotalPages(JsonNode payload, boolean emptyPage) {
        if (payload == null || payload.isNull()) {
            return emptyPage ? 0 : 1;
        }
        if (payload.has("totalPages")) {
            return Math.max(0, payload.get("totalPages").asInt(0));
        }
        if (payload.has("pageCount")) {
            return Math.max(0, payload.get("pageCount").asInt(0));
        }
        return emptyPage ? 0 : 1;
    }

    public void acceptOrder(TrendyolGoDtos.Credentials credentials, String orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("packageId", orderId);
        body.put("preparationTime", properties.getDefaultPreparationMinutes());
        exchange(HttpMethod.PUT, expand(properties.getPaths().getOrderAccept(), credentials, null), credentials, body);
    }

    public void rejectOrder(TrendyolGoDtos.Credentials credentials, String orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("packageId", orderId);
        body.put("reasonId", properties.getDefaultCancelReasonId());
        exchange(HttpMethod.PUT, expand(properties.getPaths().getOrderReject(), credentials, null), credentials, body);
    }

    public void cancelOrder(TrendyolGoDtos.Credentials credentials, String orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("packageId", orderId);
        body.put("reasonId", properties.getDefaultCancelReasonId());
        exchange(HttpMethod.PUT, expand(properties.getPaths().getOrderCancel(), credentials, null), credentials, body);
    }

    public void markReady(TrendyolGoDtos.Credentials credentials, String orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("packageId", orderId);
        exchange(HttpMethod.PUT, expand(properties.getPaths().getOrderReady(), credentials, null), credentials, body);
    }

    private JsonNode exchange(HttpMethod method, String path, TrendyolGoDtos.Credentials credentials, Object body) {
        int attempts = Math.max(1, properties.getMaxAttempts());
        RestClientResponseException lastResponse = null;
        Exception lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                RestClient.RequestBodySpec spec = restClient.method(method)
                        .uri(path)
                        .headers(headers -> applyAuth(headers, credentials))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON);
                if (body != null) {
                    spec.body(body);
                }
                String raw = spec.retrieve().body(String.class);
                if (raw == null || raw.isBlank()) {
                    return objectMapper.nullNode();
                }
                return objectMapper.readTree(raw);
            } catch (RestClientResponseException exception) {
                lastResponse = exception;
                if (!retryable(exception.getStatusCode().value()) || attempt == attempts) {
                    throw wrap(exception);
                }
            } catch (TrendyolGoClientException exception) {
                throw exception;
            } catch (Exception exception) {
                lastError = exception;
                if (attempt == attempts) {
                    throw new TrendyolGoClientException("Uber Eats Trendyol Go yanıt vermedi", exception);
                }
            }
        }
        if (lastResponse != null) {
            throw wrap(lastResponse);
        }
        throw new TrendyolGoClientException("Uber Eats Trendyol Go yanıt vermedi", lastError);
    }

    private void applyAuth(HttpHeaders headers, TrendyolGoDtos.Credentials credentials) {
        headers.set(HttpHeaders.AUTHORIZATION, authorization(credentials));
        headers.set(HttpHeaders.USER_AGENT, userAgent(credentials));
    }

    private TrendyolGoClientException wrap(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new TrendyolGoClientException("TGO kimlik bilgileri reddedildi");
        }
        log.warn("TGO HTTP {} {}", status, exception.getResponseBodyAsString());
        return new TrendyolGoClientException("Uber Eats Trendyol Go isteği başarısız oldu");
    }

    private boolean retryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private String expand(String template, TrendyolGoDtos.Credentials credentials, String orderId) {
        if (template == null || template.isBlank()) {
            throw new TrendyolGoClientException("TGO yol şablonu tanımsız");
        }
        String path = template
                .replace("{sellerId}", nullToEmpty(credentials.getSellerId()))
                .replace("{supplierId}", nullToEmpty(credentials.getSellerId()))
                .replace("{restaurantId}", nullToEmpty(credentials.getRestaurantId()))
                .replace("{storeId}", nullToEmpty(credentials.getRestaurantId()))
                .replace("{orderId}", nullToEmpty(orderId));
        if (path.contains("{")) {
            throw new TrendyolGoClientException("TGO yolunda eksik parametre var");
        }
        return path;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void requireRestaurant(TrendyolGoDtos.Credentials credentials) {
        if (credentials.getRestaurantId() == null || credentials.getRestaurantId().isBlank()) {
            throw new TrendyolGoClientException("Restoran seçilmedi");
        }
    }

    private String authorization(TrendyolGoDtos.Credentials credentials) {
        String raw = credentials.getApiKey() + ":" + credentials.getApiSecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String userAgent(TrendyolGoDtos.Credentials credentials) {
        return credentials.getSellerId() + " - " + properties.getUserAgentName();
    }
}
