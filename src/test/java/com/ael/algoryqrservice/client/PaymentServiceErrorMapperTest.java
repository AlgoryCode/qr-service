package com.ael.algoryqrservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceErrorMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void httpStatus_whenValidation_thenKeep400() {
        assertThat(PaymentServiceErrorMapper.httpStatus(400)).isEqualTo(400);
    }

    @Test
    void httpStatus_whenUnauthorized_thenMapToBadGateway() {
        assertThat(PaymentServiceErrorMapper.httpStatus(401)).isEqualTo(502);
        assertThat(PaymentServiceErrorMapper.httpStatus(403)).isEqualTo(502);
    }

    @Test
    void httpStatus_whenServerError_thenMapToBadGateway() {
        assertThat(PaymentServiceErrorMapper.httpStatus(500)).isEqualTo(502);
        assertThat(PaymentServiceErrorMapper.httpStatus(404)).isEqualTo(404);
    }

    @Test
    void detail_whenFieldErrorsPresent_thenJoinFieldMessages() {
        String body = """
                {"message":"Validation failed","errorCode":"VALIDATION_ERROR",\
                "fieldErrors":[{"field":"buyer.email","message":"must not be blank"}]}
                """;

        assertThat(PaymentServiceErrorMapper.detail(objectMapper, body))
                .isEqualTo("buyer.email: must not be blank");
    }

    @Test
    void detail_whenProblemDetail_thenReadDetailField() {
        String body = """
                {"type":"about:blank","title":"Not Found","status":404,\
                "detail":"No static resource api/v1/payment-methods/verification."}
                """;

        assertThat(PaymentServiceErrorMapper.detail(objectMapper, body))
                .isEqualTo("No static resource api/v1/payment-methods/verification.");
    }

    @Test
    void detail_whenBlank_thenReturnNull() {
        assertThat(PaymentServiceErrorMapper.detail(objectMapper, "  ")).isNull();
    }
}
