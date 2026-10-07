package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.AssignedProduct;
import com.ael.algoryqrservice.client.dto.EntitlementQuantityRequest;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.client.dto.ExternalConsumeResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.client.dto.ExternalProductAccessResponse;
import com.ael.algoryqrservice.config.FulfillmentExternalProperties;
import com.ael.algoryqrservice.exception.FulfillmentQuotaExceededException;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "fulfillment.external", name = "enabled", havingValue = "true")
public class FulfillmentServiceClient {

    private static final String ACTIVE_PACKAGE_PATH = "/api/v1/users/{userId}/active-package";
    private static final String PACKAGE_PATH = "/api/v1/users/{userId}/package";
    private static final String ENTITLEMENTS_PATH = "/api/v1/users/{userId}/entitlements";
    private static final String PRODUCT_ACCESS_PATH = "/api/v1/users/{userId}/products/{productCode}";
    private static final String CONSUME_PATH = "/api/v1/users/{userId}/entitlements/consume";
    private static final String RELEASE_PATH = "/api/v1/users/{userId}/entitlements/release";
    private static final ParameterizedTypeReference<List<ExternalEntitlementResponse>> ENTITLEMENTS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient.Builder restClientBuilder;
    private final FulfillmentExternalProperties properties;

    public void createPackage(Long userId, String packageCode, int periodDays) {
        createPackage(userId, packageCode, periodDays, List.of(), List.of());
    }

    public void createPackage(
            Long userId,
            String packageCode,
            int periodDays,
            List<AssignedProduct> items,
            List<AssignedProduct> modules
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("packageCode", packageCode);
        body.put("periodDays", periodDays);
        body.put("items", items == null ? List.of() : items);
        body.put("modules", modules == null ? List.of() : modules);
        try {
            restClientBuilder.build()
                    .post()
                    .uri(properties.getBaseUrl() + PACKAGE_PATH, userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(properties.getAuthHeader(), properties.getAuthToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                return;
            }
            throw unavailable(userId, exception);
        } catch (RestClientException exception) {
            throw unavailable(userId, exception);
        }
    }

    public Optional<ExternalActivePackageResponse> findActivePackage(Long userId) {
        try {
            ActivePackageLookup lookup = lookupActivePackage(userId);
            if (lookup instanceof ActivePackageLookup.Found found) {
                return Optional.of(found.value());
            }
            return Optional.empty();
        } catch (FulfillmentUnavailableException exception) {
            return Optional.empty();
        }
    }

    public ActivePackageLookup lookupActivePackage(Long userId) {
        try {
            ExternalActivePackageResponse response = request(ACTIVE_PACKAGE_PATH, userId)
                    .retrieve()
                    .body(ExternalActivePackageResponse.class);
            if (response == null) {
                return new ActivePackageLookup.Absent();
            }
            return new ActivePackageLookup.Found(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return new ActivePackageLookup.Absent();
            }
            throw unavailable(userId, exception);
        } catch (RestClientException exception) {
            throw unavailable(userId, exception);
        }
    }

    public List<ExternalEntitlementResponse> listEntitlements(Long userId) {
        try {
            List<ExternalEntitlementResponse> body = request(ENTITLEMENTS_PATH, userId)
                    .retrieve()
                    .body(ENTITLEMENTS);
            if (body == null) {
                return List.of();
            }
            return List.copyOf(body);
        } catch (RestClientResponseException exception) {
            throw unavailable(userId, exception);
        } catch (RestClientException exception) {
            throw unavailable(userId, exception);
        }
    }

    public ExternalProductAccessResponse findProductAccess(Long userId, String productCode) {
        try {
            ExternalProductAccessResponse body = restClientBuilder.build()
                    .get()
                    .uri(properties.getBaseUrl() + PRODUCT_ACCESS_PATH, userId, productCode)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(properties.getAuthHeader(), properties.getAuthToken())
                    .retrieve()
                    .body(ExternalProductAccessResponse.class);
            if (body == null) {
                return new ExternalProductAccessResponse(false, null, null, null);
            }
            return body;
        } catch (RestClientResponseException exception) {
            throw unavailable(userId, exception);
        } catch (RestClientException exception) {
            throw unavailable(userId, exception);
        }
    }

    public ExternalConsumeResponse consume(Long userId, String productCode, int quantity) {
        return postQuantity(CONSUME_PATH, userId, productCode, quantity);
    }

    public ExternalConsumeResponse release(Long userId, String productCode, int quantity) {
        return postQuantity(RELEASE_PATH, userId, productCode, quantity);
    }

    private ExternalConsumeResponse postQuantity(String path, Long userId, String productCode, int quantity) {
        try {
            ExternalConsumeResponse body = restClientBuilder.build()
                    .post()
                    .uri(properties.getBaseUrl() + path, userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(properties.getAuthHeader(), properties.getAuthToken())
                    .body(new EntitlementQuantityRequest(productCode, quantity))
                    .retrieve()
                    .body(ExternalConsumeResponse.class);
            if (body == null) {
                throw unavailable(userId, new RestClientException("Empty fulfillment response"));
            }
            return body;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new FulfillmentQuotaExceededException();
            }
            throw unavailable(userId, exception);
        } catch (RestClientException exception) {
            throw unavailable(userId, exception);
        }
    }

    private RestClient.RequestHeadersSpec<?> request(String path, Long userId) {
        return restClientBuilder.build()
                .get()
                .uri(properties.getBaseUrl() + path, userId)
                .accept(MediaType.APPLICATION_JSON)
                .header(properties.getAuthHeader(), properties.getAuthToken());
    }

    private FulfillmentUnavailableException unavailable(Long userId, Exception exception) {
        log.warn(
                "Fulfillment service call failed. userId={} reason={}",
                userId,
                exception.getMessage()
        );
        return new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor");
    }
}
