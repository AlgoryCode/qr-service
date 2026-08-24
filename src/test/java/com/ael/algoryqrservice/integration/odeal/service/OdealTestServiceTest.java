package com.ael.algoryqrservice.integration.odeal.service;

import com.ael.algoryqrservice.integration.odeal.client.OdealClient;
import com.ael.algoryqrservice.integration.odeal.config.OdealProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class OdealTestServiceTest {

    @Mock
    private OdealClient odealClient;

    private OdealProperties properties;
    private OdealTestService service;

    @BeforeEach
    void setUp() {
        properties = new OdealProperties();
        properties.setEnabled(true);
        properties.setTestApiKey("odeal-test-local");
        properties.setExternalDeviceKey("KASA-01");
        properties.setDefaultVatRatio(10);
        service = new OdealTestService(odealClient, properties, new ObjectMapper());
    }

    @Test
    void buildSampleBasket_whenDefaultAmount_thenBuildsTwoItemBasket() {
        JsonNode basket = service.buildSampleBasket("CREDITCARD", null);

        assertThat(basket.get("basketType").asText()).isEqualTo("SIMPLE");
        assertThat(basket.get("externalDeviceKey").asText()).isEqualTo("KASA-01");
        assertThat(basket.get("price").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(basket.get("items")).hasSize(2);
        assertThat(basket.get("paymentOptions").get(0).get("type").asText()).isEqualTo("CREDITCARD");
        assertThat(basket.get("paymentOptions").get(0).get("amount").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(basket.get("referenceCode").asText()).startsWith("test-");
    }

    @Test
    void parseAmount_whenTurkishFormat_thenParses() {
        assertThat(service.parseAmount("250,00")).isEqualByComparingTo("250.00");
        assertThat(service.parseAmount("1.250,00")).isEqualByComparingTo("1250.00");
        assertThat(service.parseAmount("250 TL")).isEqualByComparingTo("250.00");
    }

    @Test
    void parseAmount_whenZeroOrNegative_thenBadRequest() {
        assertThatThrownBy(() -> service.parseAmount("0"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Geçersiz amount");
        assertThatThrownBy(() -> service.parseAmount("-10"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Geçersiz amount");
    }

    @Test
    void parseAmount_whenInvalid_thenBadRequest() {
        assertThatThrownBy(() -> service.parseAmount("abc"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Geçersiz amount");
    }

    @Test
    void buildSampleBasket_whenCustomAmount_thenUsesSingleItem() {
        JsonNode basket = service.buildSampleBasket("CASH", service.parseAmount("100.00"));

        assertThat(basket.get("price").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(basket.get("items")).hasSize(1);
        assertThat(basket.get("paymentOptions").get(0).get("type").asText()).isEqualTo("CASH");
    }

    @Test
    void resolveAmount_whenDuplicateParamsWithInvalidBodyValue_thenUsesValidQueryValue() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/integrations/odeal/test/basket/sample");
        request.addParameter("amount", "250");
        request.addParameter("amount", "abc");

        assertThat(service.resolveAmount(request)).isEqualTo("250");
    }

    @Test
    void resolvePaymentType_whenDuplicateParams_thenUsesFirstValidValue() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/integrations/odeal/test/basket/sample");
        request.addParameter("paymentType", "CREDITCARD");
        request.addParameter("paymentType", "CREDITCARD");

        assertThat(service.resolvePaymentType(request)).isEqualTo("CREDITCARD");
    }

    @Test
    void validateTestApiKey_whenInvalid_thenUnauthorized() {
        assertThatThrownBy(() -> service.validateTestApiKey("wrong-key"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Geçersiz test API anahtarı");
    }

    @Test
    void validateTestApiKey_whenValid_thenPasses() {
        service.validateTestApiKey("odeal-test-local");
    }
}
