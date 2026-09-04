package com.ael.algoryqrservice.integration.trendyolgo.controller;

import com.ael.algoryqrservice.integration.trendyolgo.service.TrendyolGoOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations/ubereats/webhooks")
@RequiredArgsConstructor
public class TrendyolGoWebhookController {

    private final TrendyolGoOrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<Void> orders(
            @RequestHeader(value = "x-api-key", required = false) String apiKey,
            @RequestBody JsonNode payload
    ) {
        orderService.ingestWebhook(apiKey, payload);
        return ResponseEntity.ok().build();
    }
}
