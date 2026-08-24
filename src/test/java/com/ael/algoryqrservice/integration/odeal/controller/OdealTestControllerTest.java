package com.ael.algoryqrservice.integration.odeal.controller;

import com.ael.algoryqrservice.exception.GlobalExceptionHandler;
import com.ael.algoryqrservice.integration.odeal.model.dto.OdealTestDtos;
import com.ael.algoryqrservice.integration.odeal.service.OdealTestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OdealTestControllerTest {

    @Mock
    private OdealTestService odealTestService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OdealTestController(odealTestService, objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getUnits_whenMissingTestKey_thenUnauthorized() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Geçersiz test API anahtarı"
        )).when(odealTestService).validateTestApiKey(null);

        mockMvc.perform(get("/integrations/odeal/test/units"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUnits_whenValidTestKey_thenReturnsOdealResponse() throws Exception {
        when(odealTestService.getUnits()).thenReturn(OdealTestDtos.ProxyResponse.builder()
                .statusCode(200)
                .body(Map.of("ok", true))
                .rawBody("{\"ok\":true}")
                .build());

        mockMvc.perform(get("/integrations/odeal/test/units")
                        .header(OdealTestService.TEST_KEY_HEADER, "odeal-test-local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.body.ok").value(true));
    }

    @Test
    void sendSampleBasket_whenValidTestKey_thenReturnsOdealResponse() throws Exception {
        when(odealTestService.sendSampleBasket(any())).thenReturn(OdealTestDtos.ProxyResponse.builder()
                .statusCode(201)
                .body(Map.of("referenceCode", "test-123"))
                .rawBody("{\"referenceCode\":\"test-123\"}")
                .build());

        mockMvc.perform(post("/integrations/odeal/test/basket/sample")
                        .header(OdealTestService.TEST_KEY_HEADER, "odeal-test-local")
                        .param("paymentType", "CREDITCARD")
                        .param("amount", "250"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body.referenceCode").value("test-123"));
    }

    @Test
    void sendBasket_whenValidTestKey_thenForwardsBody() throws Exception {
        when(odealTestService.sendBasket(any())).thenReturn(OdealTestDtos.ProxyResponse.builder()
                .statusCode(200)
                .body(Map.of("accepted", true))
                .rawBody("{\"accepted\":true}")
                .build());

        mockMvc.perform(post("/integrations/odeal/test/basket")
                        .header(OdealTestService.TEST_KEY_HEADER, "odeal-test-local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceCode\":\"manual-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.accepted").value(true));
    }
}
