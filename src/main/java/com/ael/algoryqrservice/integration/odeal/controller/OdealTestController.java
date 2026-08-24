package com.ael.algoryqrservice.integration.odeal.controller;

import com.ael.algoryqrservice.integration.odeal.model.dto.OdealTestDtos;
import com.ael.algoryqrservice.integration.odeal.service.OdealTestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/integrations/odeal/test")
@RequiredArgsConstructor
public class OdealTestController {

    private final OdealTestService odealTestService;
    private final ObjectMapper objectMapper;

    @GetMapping("/units")
    public ResponseEntity<OdealTestDtos.ProxyResponse> getUnits(
            @RequestHeader(value = OdealTestService.TEST_KEY_HEADER, required = false) String testApiKey
    ) {
        odealTestService.validateTestApiKey(testApiKey);
        OdealTestDtos.ProxyResponse response = odealTestService.getUnits();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/basket/sample")
    public ResponseEntity<OdealTestDtos.ProxyResponse> sendSampleBasket(
            @RequestHeader(value = OdealTestService.TEST_KEY_HEADER, required = false) String testApiKey,
            HttpServletRequest request
    ) {
        odealTestService.validateTestApiKey(testApiKey);
        OdealTestDtos.ProxyResponse response = odealTestService.sendSampleBasket(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/basket")
    public ResponseEntity<OdealTestDtos.ProxyResponse> sendBasket(
            @RequestHeader(value = OdealTestService.TEST_KEY_HEADER, required = false) String testApiKey,
            @RequestBody Map<String, Object> body
    ) {
        odealTestService.validateTestApiKey(testApiKey);
        OdealTestDtos.ProxyResponse response = odealTestService.sendBasket(objectMapper.valueToTree(body));
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
