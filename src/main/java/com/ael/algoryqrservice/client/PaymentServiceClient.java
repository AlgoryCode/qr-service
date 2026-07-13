package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsResponse;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class PaymentServiceClient {

    private final RestClient restClient;

    public PaymentServiceClient(RestClient.Builder restClientBuilder, PaymentClientProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getUrl())
                .build();
    }

    public PaymentThreeDsResponse initializeThreeDsPayment(PaymentThreeDsRequest request) {
        try {
            return restClient.post()
                    .uri("/payments/three-ds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PaymentThreeDsResponse.class);
        } catch (RestClientResponseException exception) {
            log.error("Payment service error. status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new PaymentServiceException("Ödeme servisi hatası: " + exception.getStatusCode());
        } catch (Exception exception) {
            log.error("Payment service unreachable", exception);
            throw new PaymentServiceException("Ödeme servisine ulaşılamadı");
        }
    }
}
