package com.ael.algoryqrservice.integration.yemeksepeti.controller;

import com.ael.algoryqrservice.integration.yemeksepeti.service.YemekSepetiOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations/yemek-sepeti/webhooks")
@RequiredArgsConstructor
public class YemekSepetiWebhookController {

    private final YemekSepetiOrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<Void> orders(
            @RequestHeader(value = "x-api-key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody JsonNode payload
    ) {
        orderService.ingestWebhook(apiKey, authorization, payload);
        return ResponseEntity.ok().build();
    }
}
