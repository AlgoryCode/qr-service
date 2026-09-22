package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.config.FulfillmentExternalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "fulfillment.external", name = "enabled", havingValue = "true")
public class FulfillmentServiceClient {

    private final RestClient.Builder restClientBuilder;
    private final FulfillmentExternalProperties properties;

    public Optional<ExternalActivePackageResponse> findActivePackage(Long userId) {
        try {
            ExternalActivePackageResponse response = restClientBuilder.build()
                    .get()
                    .uri(properties.getBaseUrl() + "/api/v1/users/{userId}/active-package", userId)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(properties.getAuthHeader(), properties.getAuthToken())
                    .retrieve()
                    .body(ExternalActivePackageResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception exception) {
            log.warn(
                    "Fulfillment service active-package lookup failed. userId={} reason={}",
                    userId,
                    exception.getMessage()
            );
            return Optional.empty();
        }
    }
}
