package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentCardStorageSessionResponse;
import com.ael.algoryqrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsResponse;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = restClientBuilder
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public PaymentThreeDsResponse initializeThreeDsPayment(PaymentThreeDsRequest request) {
        return createPayment(request, "/payments/three-ds");
    }

    public PaymentThreeDsResponse createDirectPayment(PaymentThreeDsRequest request) {
        return createPayment(request, "/payments/stored-card");
    }

    public BillingPaymentDtos.StoredCardCharge chargeStoredCard(Long userId, PaymentThreeDsRequest request) {
        try {
            if (userId == null) {
                throw new PaymentServiceException("Ödeme için kullanıcı kimliği zorunludur");
            }
            Map<?, ?> response = restClient.post()
                    .uri("/payments/stored-card")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders(userId))
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null) {
                throw new PaymentServiceException("Kayitli kart odemesi yaniti bos");
            }
            return new BillingPaymentDtos.StoredCardCharge(
                    stringValue(response.get("conversationId")),
                    stringValue(response.get("status"))
            );
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String detail = extractErrorMessage(exception);
            throw new PaymentServiceException(
                    detail == null || detail.isBlank()
                            ? "Kayitli kart odemesi basarisiz: " + exception.getStatusCode()
                            : "Kayitli kart odemesi basarisiz: " + detail
            );
        } catch (Exception exception) {
            throw new PaymentServiceException("Odeme servisine ulasilamadi");
        }
    }

    public PaymentCheckoutFormResponse initializeCheckoutForm(Long userId, PaymentCheckoutFormRequest request) {
        try {
            if (userId == null) {
                throw new PaymentServiceException("Ödeme için kullanıcı kimliği zorunludur");
            }
            log.info(
                    "Checkout form payment requested. userId={}, conversationId={}, amount={}, currency={}",
                    userId,
                    request.getConversationId(),
                    request.getPaidPrice() != null ? request.getPaidPrice() : request.getPrice(),
                    request.getCurrency()
            );
            PaymentCheckoutFormResponse response = restClient.post()
                    .uri("/payments/checkout-form")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders(userId))
                    .body(request)
                    .retrieve()
                    .body(PaymentCheckoutFormResponse.class);
            log.info(
                    "Checkout form payment initialized. userId={}, conversationId={}",
                    userId,
                    response != null ? response.getConversationId() : request.getConversationId()
            );
            return response;
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.error(
                    "Checkout form init failed. userId={}, conversationId={}, status={} body={}",
                    userId,
                    request.getConversationId(),
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );
            throw paymentError(exception, "Ödeme servisi hatası: ");
        } catch (Exception exception) {
            log.error("Payment service unreachable", exception);
            throw new PaymentServiceException("Ödeme servisine ulaşılamadı");
        }
    }

    public PaymentCardStorageSessionResponse initiateCardVerification(Long userId, PaymentCardVerificationRequest request) {
        try {
            if (userId == null) {
                throw new PaymentServiceException("Kart dogrulama icin kullanici kimligi zorunludur");
            }
            log.info(
                    "Card verification request sent. userId={} paymentId={}",
                    userId,
                    request == null ? null : request.getConversationId()
            );
            PaymentCardStorageSessionResponse response = restClient.post()
                    .uri("/api/v1/payment-methods/verification")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders(userId))
                    .body(request)
                    .retrieve()
                    .body(PaymentCardStorageSessionResponse.class);
            log.info(
                    "Card verification request accepted. userId={} paymentId={}",
                    userId,
                    response == null ? null : response.getConversationId()
            );
            return response;
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.error(
                    "Card verification request failed. userId={} paymentId={} status={}",
                    userId,
                    request == null ? null : request.getConversationId(),
                    exception.getStatusCode().value()
            );
            throw paymentError(exception, "Kart dogrulama servisi hatasi: ");
        } catch (Exception exception) {
            log.error(
                    "Card verification request failed. userId={} paymentId={} reason=UNREACHABLE",
                    userId,
                    request == null ? null : request.getConversationId()
            );
            throw new PaymentServiceException("Odeme servisine ulasilamadi");
        }
    }

    public BillingPaymentDtos.RefundablePayment getCardVerificationStatus(Long userId, String conversationId) {
        return getRefundablePayment(userId, conversationId);
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
            log.info("Payment method create requested. userId={}", userId);
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
            BillingPaymentDtos.PaymentMethod method = toPaymentMethod(response);
            log.info(
                    "Payment method created. userId={}, paymentMethodId={}, lastFour={}",
                    userId,
                    method.id(),
                    method.lastFour()
            );
            return method;
        } catch (RestClientResponseException exception) {
            log.error("Payment method create failed. userId={}, status={}", userId, exception.getStatusCode());
            throw new PaymentServiceException("Kart kaydedilemedi: " + exception.getStatusCode());
        }
    }

    public void deletePaymentMethod(Long userId, String paymentMethodId) {
        try {
            log.info("Payment method delete requested. userId={}, paymentMethodId={}", userId, paymentMethodId);
            restClient.delete()
                    .uri("/api/v1/payment-methods/{id}", paymentMethodId)
                    .headers(authHeaders(userId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Payment method deleted. userId={}, paymentMethodId={}", userId, paymentMethodId);
        } catch (RestClientResponseException exception) {
            log.error(
                    "Payment method delete failed. userId={}, paymentMethodId={}, status={}",
                    userId,
                    paymentMethodId,
                    exception.getStatusCode()
            );
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
        try {
            List<BillingPaymentDtos.Subscription> response = restClient.get()
                    .uri("/api/v1/subscriptions")
                    .headers(authHeaders(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response == null ? Collections.emptyList() : response;
        } catch (RestClientResponseException exception) {
            log.error("Subscription list failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException("Abonelikler alinamadi: " + exception.getStatusCode());
        }
    }

    public BillingPaymentDtos.Subscription bootstrapSubscription(
            Long userId,
            String serviceName,
            String sourceReferenceId,
            String conversationId,
            BigDecimal amount,
            String currency,
            Integer billingIntervalMonths,
            Long paymentMethodId,
            LocalDateTime nextChargeAt,
            Map<String, Object> sourceMetadata
    ) {
        return bootstrapSubscription(
                userId,
                serviceName,
                sourceReferenceId,
                conversationId,
                amount,
                currency,
                billingIntervalMonths,
                paymentMethodId,
                nextChargeAt,
                sourceMetadata,
                null,
                null,
                null
        );
    }

    public BillingPaymentDtos.Subscription bootstrapSubscription(
            Long userId,
            String serviceName,
            String sourceReferenceId,
            String conversationId,
            BigDecimal amount,
            String currency,
            Integer billingIntervalMonths,
            Long paymentMethodId,
            LocalDateTime nextChargeAt,
            Map<String, Object> sourceMetadata,
            Object buyer,
            Object shippingAddress,
            Object billingAddress
    ) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("serviceName", serviceName);
            body.put("sourceReferenceId", sourceReferenceId);
            body.put("conversationId", conversationId);
            body.put("amount", amount);
            body.put("currency", currency);
            body.put("billingIntervalMonths", billingIntervalMonths);
            body.put("paymentMethodId", paymentMethodId);
            body.put("nextChargeAt", nextChargeAt);
            body.put("sourceMetadata", sourceMetadata);
            if (buyer != null) {
                body.put("buyer", buyer);
            }
            if (shippingAddress != null) {
                body.put("shippingAddress", shippingAddress);
            }
            if (billingAddress != null) {
                body.put("billingAddress", billingAddress);
            }
            return restClient.post()
                    .uri("/api/v1/subscriptions/bootstrap")
                    .headers(authHeaders(userId))
                    .body(body)
                    .retrieve()
                    .body(BillingPaymentDtos.Subscription.class);
        } catch (RestClientResponseException exception) {
            log.error("Subscription bootstrap failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException("Abonelik baslatilamadi: " + exception.getStatusCode());
        }
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

    public BillingPaymentDtos.Subscription cancelSubscriptionAtPeriodEnd(Long userId, String subscriptionId) {
        try {
            return restClient.post()
                    .uri("/api/v1/subscriptions/{id}/cancel-at-period-end", subscriptionId)
                    .headers(authHeaders(userId))
                    .retrieve()
                    .body(BillingPaymentDtos.Subscription.class);
        } catch (RestClientResponseException exception) {
            log.error("Subscription cancel-at-period-end failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException(
                    "Abonelik donem sonu iptali basarisiz: " + exception.getStatusCode()
            );
        }
    }

    public BillingPaymentDtos.Subscription resumeSubscription(Long userId, String subscriptionId) {
        try {
            return restClient.post()
                    .uri("/api/v1/subscriptions/{id}/resume", subscriptionId)
                    .headers(authHeaders(userId))
                    .retrieve()
                    .body(BillingPaymentDtos.Subscription.class);
        } catch (RestClientResponseException exception) {
            log.error("Subscription resume failed. status={}", exception.getStatusCode());
            throw new PaymentServiceException("Abonelik yenileme geri alma basarisiz: " + exception.getStatusCode());
        }
    }

    public Optional<BillingPaymentDtos.RefundablePayment> findPayment(Long userId, String conversationId) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri("/payments/{conversationId}", conversationId)
                    .headers(authHeaders(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(mapRefundablePayment(response));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                log.debug("Payment not found. conversationId={}", conversationId);
                return Optional.empty();
            }
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

    public BillingPaymentDtos.PaymentDetail getPaymentDetail(String conversationId) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri("/payments/{conversationId}", conversationId)
                    .headers(authHeaders(null))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null) {
                throw new PaymentServiceException("Odeme kaydi bulunamadi");
            }
            return mapPaymentDetail(response);
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new PaymentServiceException("Odeme kaydi bulunamadi");
            }
            String detail = extractErrorMessage(exception);
            throw new PaymentServiceException(
                    detail == null || detail.isBlank()
                            ? "Odeme kaydi alinamadi: " + exception.getStatusCode()
                            : "Odeme kaydi alinamadi: " + detail
            );
        }
    }

    public BillingPaymentDtos.PaymentPage searchPayments(
            String query,
            String status,
            String paymentType,
            String paymentStyle,
            String accountId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Boolean verificationOnly,
            int page,
            int size
    ) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/payments")
                                .queryParam("page", page)
                                .queryParam("size", size);
                        if (query != null && !query.isBlank()) {
                            builder.queryParam("q", query);
                        }
                        if (status != null && !status.isBlank()) {
                            builder.queryParam("status", status);
                        }
                        if (paymentType != null && !paymentType.isBlank()) {
                            builder.queryParam("paymentType", paymentType);
                        }
                        if (paymentStyle != null && !paymentStyle.isBlank()) {
                            builder.queryParam("paymentStyle", paymentStyle);
                        }
                        if (accountId != null && !accountId.isBlank()) {
                            builder.queryParam("accountId", accountId);
                        }
                        if (createdFrom != null) {
                            builder.queryParam("createdFrom", createdFrom);
                        }
                        if (createdTo != null) {
                            builder.queryParam("createdTo", createdTo);
                        }
                        if (verificationOnly != null) {
                            builder.queryParam("verificationOnly", verificationOnly);
                        }
                        return builder.build();
                    })
                    .headers(authHeaders(null))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null) {
                return new BillingPaymentDtos.PaymentPage(List.of(), page, size, 0, 0, false);
            }
            return mapPaymentPage(response, page, size);
        } catch (RestClientResponseException exception) {
            String detail = extractErrorMessage(exception);
            log.error("Payment search failed. status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new PaymentServiceException(
                    detail == null || detail.isBlank()
                            ? "Odeme listesi alinamadi: " + exception.getStatusCode()
                            : "Odeme listesi alinamadi: " + detail
            );
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
            return mapRefundablePayment(response);
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

    private BillingPaymentDtos.RefundablePayment mapRefundablePayment(Map<?, ?> response) {
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
    }

    private BillingPaymentDtos.PaymentDetail mapPaymentDetail(Map<?, ?> response) {
        BigDecimal paidPrice = decimalValue(response.get("paidPrice"));
        BigDecimal refundedAmount = decimalValue(response.get("refundedAmount"));
        if (paidPrice == null) {
            paidPrice = BigDecimal.ZERO;
        }
        if (refundedAmount == null) {
            refundedAmount = BigDecimal.ZERO;
        }
        BigDecimal remaining = paidPrice.subtract(refundedAmount).max(BigDecimal.ZERO);
        Boolean verificationOnly = booleanValue(response.get("verificationOnly"));
        return new BillingPaymentDtos.PaymentDetail(
                stringValue(response.get("conversationId")),
                stringValue(response.get("paymentId")),
                stringValue(response.get("paymentTransactionId")),
                stringValue(response.get("basketId")),
                stringValue(response.get("accountId")),
                stringValue(response.get("buyerEmail")),
                stringValue(response.get("buyerName")),
                stringValue(response.get("serviceName")),
                stringValue(response.get("sourceReferenceId")),
                decimalValue(response.get("price")),
                paidPrice,
                refundedAmount,
                remaining,
                stringValue(response.get("currency")),
                stringValue(response.get("status")),
                stringValue(response.get("paymentType")),
                stringValue(response.get("paymentStyle")),
                intValue(response.get("bankInstallmentCount")),
                stringValue(response.get("subscriptionId")),
                intValue(response.get("billingCycleNumber")),
                verificationOnly != null && verificationOnly,
                stringValue(response.get("errorCode")),
                stringValue(response.get("errorMessage")),
                localDateTimeValue(response.get("createdAt")),
                localDateTimeValue(response.get("updatedAt"))
        );
    }

    private BillingPaymentDtos.PaymentPage mapPaymentPage(Map<?, ?> response, int fallbackPage, int fallbackSize) {
        List<BillingPaymentDtos.PaymentDetail> content = List.of();
        Object rawContent = response.get("content");
        if (rawContent instanceof List<?> list) {
            content = list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<?, ?>) item)
                    .map(this::mapPaymentDetail)
                    .toList();
        }
        Integer pageValue = intValue(firstNonNull(response.get("number"), response.get("page")));
        Integer sizeValue = intValue(response.get("size"));
        int page = pageValue == null ? fallbackPage : pageValue;
        int size = sizeValue == null ? fallbackSize : sizeValue;
        long totalElements = longValue(firstNonNull(response.get("totalElements"), (long) content.size()));
        Integer totalPagesValue = intValue(response.get("totalPages"));
        int totalPages = totalPagesValue == null ? 0 : totalPagesValue;
        boolean hasNext = resolveHasNext(response, page, totalPages);
        return new BillingPaymentDtos.PaymentPage(content, page, size, totalElements, totalPages, hasNext);
    }

    private boolean resolveHasNext(Map<?, ?> response, int page, int totalPages) {
        Object hasNext = response.get("hasNext");
        if (hasNext instanceof Boolean value) {
            return value;
        }
        Object last = response.get("last");
        if (last instanceof Boolean value) {
            return !value;
        }
        return page + 1 < totalPages;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.endsWith("Z") || text.contains("+")) {
            return java.time.OffsetDateTime.parse(text).toLocalDateTime();
        }
        return LocalDateTime.parse(text);
    }

    public BillingPaymentDtos.RefundResult refundPayment(
            Long userId,
            String conversationId,
            BigDecimal amount,
            String clientIp
    ) {
        try {
            log.info(
                    "Payment refund requested. userId={}, conversationId={}, amount={}",
                    userId,
                    conversationId,
                    amount
            );
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
            BillingPaymentDtos.RefundResult result = new BillingPaymentDtos.RefundResult(
                    stringValue(response.get("conversationId")),
                    stringValue(response.get("paymentTransactionId")),
                    decimalValue(response.get("refundedPrice")),
                    stringValue(response.get("status"))
            );
            log.info(
                    "Payment refund completed. userId={}, conversationId={}, status={}, refundedPrice={}",
                    userId,
                    result.conversationId(),
                    result.status(),
                    result.refundedPrice()
            );
            return result;
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String detail = extractErrorMessage(exception);
            log.error(
                    "Payment refund failed. userId={}, conversationId={}, status={}",
                    userId,
                    conversationId,
                    exception.getStatusCode()
            );
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
            log.info(
                    "Payment requested. userId={}, conversationId={}, path={}, amount={}, currency={}, style={}",
                    userId,
                    request.getConversationId(),
                    path,
                    request.getPaidPrice() != null ? request.getPaidPrice() : request.getPrice(),
                    request.getCurrency(),
                    request.getPaymentStyle()
            );
            PaymentThreeDsResponse response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders(userId))
                    .body(request)
                    .retrieve()
                    .body(PaymentThreeDsResponse.class);
            log.info(
                    "Payment accepted by payment-service. userId={}, conversationId={}, path={}",
                    userId,
                    response != null ? response.getConversationId() : request.getConversationId(),
                    path
            );
            return response;
        } catch (PaymentServiceException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.error(
                    "Payment service error. userId={}, conversationId={}, path={}, status={}",
                    extractUserId(request),
                    request.getConversationId(),
                    path,
                    exception.getStatusCode()
            );
            throw paymentError(exception, "Ödeme servisi hatası: ");
        } catch (Exception exception) {
            log.error("Payment service unreachable", exception);
            throw new PaymentServiceException("Ödeme servisine ulaşılamadı");
        }
    }

    private PaymentServiceException paymentError(RestClientResponseException exception, String fallbackPrefix) {
        String detail = extractErrorMessage(exception);
        int status = PaymentServiceErrorMapper.httpStatus(exception.getStatusCode().value());
        String message = detail == null || detail.isBlank()
                ? fallbackPrefix + exception.getStatusCode()
                : fallbackPrefix + detail;
        return new PaymentServiceException(message, status);
    }

    private String extractErrorMessage(RestClientResponseException exception) {
        return PaymentServiceErrorMapper.detail(objectMapper, exception.getResponseBodyAsString());
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
