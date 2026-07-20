package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsResponse;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@Slf4j
public class PaymentServiceClient {

    private final RestClient restClient;
    private final PaymentClientProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentServiceClient(
            RestClient.Builder restClientBuilder,
            PaymentClientProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.getUrl())
                .build();
    }

    public PaymentThreeDsResponse initializeThreeDsPayment(PaymentThreeDsRequest request) {
        return createPayment(request, "/payments/three-ds");
    }

    public PaymentThreeDsResponse createDirectPayment(PaymentThreeDsRequest request) {
        return createPayment(request, "/payments");
    }

    public List<BillingPaymentDtos.PaymentMethod> getPaymentMethods(Long userId) {
        try {
            Map<String, Object> page = restClient.get()
                    .uri("/api/v1/payment-methods")
                    .headers(authHeaders(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (page == null || !(page.get("content") instanceof List<?> content)) {
                return List.of();
            }
            return content.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<?, ?>) item)
                    .map(this::toPaymentMethod)
                    .toList();
        } catch (RestClientResponseException exception) {
            log.error("Payment methods list failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException("Ödeme yöntemleri alınamadı: " + exception.getStatusCode());
        }
    }

    public BillingPaymentDtos.PaymentMethod createPaymentMethod(
            Long userId,
            String email,
            String alias,
            String cardHolderName,
            String cardNumber,
            String expireMonth,
            String expireYear
    ) {
        try {
            Map<String, Object> body = Map.of(
                    "alias", alias == null || alias.isBlank() ? "Kartım" : alias.trim(),
                    "email", email,
                    "cardHolderName", cardHolderName,
                    "cardNumber", cardNumber.replaceAll("\\D", ""),
                    "expireMonth", expireMonth,
                    "expireYear", expireYear
            );
            Map<?, ?> response = restClient.post()
                    .uri("/api/v1/payment-methods")
                    .headers(authHeaders(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null) {
                throw new PaymentServiceException("Ödeme yöntemi kaydedilemedi");
            }
            return toPaymentMethod(response);
        } catch (RestClientResponseException exception) {
            log.error("Payment method create failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException("Kart kaydedilemedi: " + exception.getStatusCode());
        }
    }

    public void deletePaymentMethod(Long userId, String paymentMethodId) {
        try {
            restClient.delete()
                    .uri("/api/v1/payment-methods/{id}", paymentMethodId)
                    .headers(authHeaders(userId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            log.error("Payment method delete failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException("Ödeme yöntemi silinemedi: " + exception.getStatusCode());
        }
    }

    public BillingPaymentDtos.InstallmentOptions getInstallmentOptions(
            BigDecimal amount,
            String currency,
            String binNumber
    ) {
        try {
            List<Map<String, Object>> providers = restClient.get()
                    .uri(uri -> uri.path("/api/v1/payment-options/installments")
                            .queryParam("binNumber", binNumber)
                            .queryParam("price", amount)
                            .queryParam("currency", currency)
                            .build())
                    .headers(authHeaders(null))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (providers == null) {
                return new BillingPaymentDtos.InstallmentOptions(List.of());
            }
            List<BillingPaymentDtos.InstallmentOption> options = providers.stream()
                    .map(provider -> provider.get("options"))
                    .filter(List.class::isInstance)
                    .map(list -> (List<?>) list)
                    .flatMap(List::stream)
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<?, ?>) item)
                    .map(this::toInstallmentOption)
                    .toList();
            return new BillingPaymentDtos.InstallmentOptions(options);
        } catch (RestClientResponseException exception) {
            log.error("Installment options failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException("Taksit seçenekleri alınamadı: " + exception.getStatusCode());
        }
    }

    public List<BillingPaymentDtos.Subscription> getSubscriptions(Long userId) {
        return Collections.emptyList();
    }

    public BillingPaymentDtos.Subscription cancelSubscription(Long userId, String subscriptionId) {
        try {
            return restClient.post()
                    .uri("/api/v1/subscriptions/{id}/cancel", subscriptionId)
                    .headers(authHeaders(userId))
                    .retrieve()
                    .body(BillingPaymentDtos.Subscription.class);
        } catch (RestClientResponseException exception) {
            log.error("Subscription cancel failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException("Abonelik iptal edilemedi: " + exception.getStatusCode());
        }
    }

    public BillingPaymentDtos.RefundablePayment getRefundablePayment(Long userId, String conversationId) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri("/payments/{conversationId}", conversationId)
                    .headers(authHeaders(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null) {
                throw new PaymentServiceException("Odeme kaydi bulunamadi");
            }
            BigDecimal paidPrice = decimalValue(response.get("paidPrice"));
            BigDecimal refundedAmount = decimalValue(response.get("refundedAmount"));
            if (paidPrice == null) {
                paidPrice = BigDecimal.ZERO;
            }
            if (refundedAmount == null) {
                refundedAmount = BigDecimal.ZERO;
            }
            BigDecimal remaining = paidPrice.subtract(refundedAmount).max(BigDecimal.ZERO);
            return new BillingPaymentDtos.RefundablePayment(
                    stringValue(response.get("conversationId")),
                    stringValue(response.get("paymentId")),
                    stringValue(response.get("paymentTransactionId")),
                    stringValue(response.get("status")),
                    paidPrice,
                    refundedAmount,
                    remaining
            );
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String detail = extractErrorMessage(exception);
            log.error(
                    "Payment retrieve failed. conversationId={} status={} body={}",
                    conversationId,
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );
            throw new PaymentServiceException(
                    detail == null || detail.isBlank()
                            ? "Odeme kaydi alinamadi: " + exception.getStatusCode()
                            : "Odeme kaydi alinamadi: " + detail
            );
        }
    }

    public BillingPaymentDtos.RefundResult refundPayment(
            Long userId,
            String conversationId,
            BigDecimal amount,
            String clientIp
    ) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("conversationId", conversationId);
            body.put("locale", "tr");
            body.put("price", amount);
            body.put("ip", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
            Map<?, ?> response = restClient.post()
                    .uri("/payments/refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders(userId))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null) {
                throw new PaymentServiceException("Iade yaniti bos");
            }
            return new BillingPaymentDtos.RefundResult(
                    stringValue(response.get("conversationId")),
                    stringValue(response.get("paymentTransactionId")),
                    decimalValue(response.get("refundedPrice")),
                    stringValue(response.get("status"))
            );
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String detail = extractErrorMessage(exception);
            log.error("Payment refund failed. status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new PaymentServiceException(
                    detail == null || detail.isBlank()
                            ? "Iade basarisiz: " + exception.getStatusCode()
                            : "Iade basarisiz: " + detail
            );
        }
    }

    private PaymentThreeDsResponse createPayment(PaymentThreeDsRequest request, String path) {
        try {
            Long userId = extractUserId(request);
            if (userId == null) {
                throw new PaymentServiceException("Ödeme için kullanıcı kimliği zorunludur");
            }
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders(userId))
                    .body(request)
                    .retrieve()
                    .body(PaymentThreeDsResponse.class);
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String detail = extractErrorMessage(exception);
            log.error("Payment service error. status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new PaymentServiceException(
                    detail == null || detail.isBlank()
                            ? "Ödeme servisi hatası: " + exception.getStatusCode()
                            : "Ödeme servisi hatası: " + detail
            );
        } catch (Exception exception) {
            log.error("Payment service unreachable", exception);
            throw new PaymentServiceException("Ödeme servisine ulaşılamadı");
        }
    }

    private String extractErrorMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<>() {
            });
            Object message = parsed.get("message");
            if (message != null && !String.valueOf(message).isBlank()) {
                return String.valueOf(message);
            }
        } catch (Exception ignored) {
            return body.length() > 300 ? body.substring(0, 300) : body;
        }
        return null;
    }

    private Long extractUserId(PaymentThreeDsRequest request) {
        if (request == null || request.getSourceMetadata() == null) {
            return null;
        }
        Object raw = request.getSourceMetadata().get("userId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        return null;
    }

    private Consumer<HttpHeaders> authHeaders(Long userId) {
        return headers -> {
            if (properties.getAuthToken() != null && !properties.getAuthToken().isBlank()) {
                headers.set(properties.getAuthHeader(), properties.getAuthToken());
            }
            if (userId != null) {
                headers.set("X-Account-Id", String.valueOf(userId));
            }
        };
    }

    private BillingPaymentDtos.PaymentMethod toPaymentMethod(Map<?, ?> item) {
        return new BillingPaymentDtos.PaymentMethod(
                stringValue(item.get("id")),
                stringValue(item.get("alias") != null ? item.get("alias") : item.get("cardAlias")),
                stringValue(item.get("brand") != null ? item.get("brand") : item.get("cardAssociation")),
                stringValue(item.get("last4") != null ? item.get("last4") : item.get("lastFourDigits")),
                intValue(item.get("expiryMonth")),
                intValue(item.get("expiryYear"))
        );
    }

    private BillingPaymentDtos.InstallmentOption toInstallmentOption(Map<?, ?> item) {
        BigDecimal totalAmount = decimalValue(
                firstNonNull(item.get("totalPrice"), item.get("totalAmount"), item.get("price"))
        );
        BigDecimal installmentAmount = decimalValue(
                firstNonNull(item.get("installmentPrice"), item.get("installmentAmount"), item.get("monthlyAmount"))
        );
        Integer count = intValue(firstNonNull(
                item.get("installmentCount"),
                item.get("installmentNumber"),
                item.get("count"),
                item.get("numberOfInstallments"),
                item.get("installments")
        ));
        if (count == null) {
            count = deriveInstallmentCount(totalAmount, installmentAmount);
        }
        return new BillingPaymentDtos.InstallmentOption(count, totalAmount, installmentAmount);
    }

    private Integer deriveInstallmentCount(BigDecimal totalAmount, BigDecimal installmentAmount) {
        if (totalAmount == null || installmentAmount == null
                || installmentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalAmount.divide(installmentAmount, 0, RoundingMode.HALF_UP).intValue();
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
