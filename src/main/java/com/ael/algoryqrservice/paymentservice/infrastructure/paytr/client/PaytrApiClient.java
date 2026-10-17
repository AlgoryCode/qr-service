package com.ael.algoryqrservice.paymentservice.infrastructure.paytr.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaytrProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class PaytrApiClient {

    private static final String PENDING = "pending";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final PaytrProperties paytrProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public PaytrApiClient(PaytrProperties paytrProperties) {
        this.paytrProperties = paytrProperties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public JsonNode getToken(Map<String, String> formFields) {
        return postJson("/odeme/api/get-token", formFields);
    }

    public JsonNode refund(Map<String, String> formFields) {
        return postJson("/odeme/iade", formFields);
    }

    public JsonNode queryStatus(Map<String, String> formFields) {
        return postJson("/odeme/durum-sorgu", formFields);
    }

    public JsonNode listStoredCards(Map<String, String> formFields) {
        JsonNode response = postJson("/odeme/capi/list", formFields);
        log.info(
                "PayTR CAPI LIST response received. status={} errorCodePresent={} errorMessagePresent={} arrayResponse={}",
                response.path("status").asText(null),
                !response.path("err_no").asText("").isBlank(),
                !response.path("err_msg").asText("").isBlank(),
                response.isArray()
        );
        return response;
    }

    public JsonNode deleteStoredCard(Map<String, String> formFields) {
        JsonNode response = postJson("/odeme/capi/delete", formFields);
        log.info(
                "PayTR CAPI DELETE response received. status={} errorCodePresent={} errorMessagePresent={}",
                response.path("status").asText(null),
                !response.path("err_no").asText("").isBlank(),
                !response.path("err_msg").asText("").isBlank()
        );
        return response;
    }

    public JsonNode chargeStoredCard(Map<String, String> formFields) {
        String raw = postRaw("/odeme", formFields);
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            ObjectNode pending = objectMapper.createObjectNode();
            pending.put("status", PENDING);
            pending.put("html", raw);
            return pending;
        }
    }

    private JsonNode postJson(String path, Map<String, String> formFields) {
        String raw = postRaw(path, formFields);
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new PaymentProcessingException("PAYTR_INVALID_RESPONSE", "PayTR yaniti JSON degil");
        }
    }

    private String postRaw(String path, Map<String, String> formFields) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        formFields.forEach(body::add);
        try {
            String raw = restClient.post()
                    .uri(paytrProperties.baseUrl() + path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new PaymentProcessingException("PAYTR_EMPTY_RESPONSE", "PayTR bos yanit dondu");
            }
            return raw;
        } catch (PaymentProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("PayTR API call failed. path={}", path, exception);
            throw new PaymentProcessingException(
                    "PAYTR_API_ERROR",
                    "PayTR API cagrisi basarisiz: " + exception.getMessage()
            );
        }
    }
}
