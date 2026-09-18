package com.ael.algoryqrservice.store.controller;

import com.ael.algoryqrservice.store.model.dto.StorePublicDtos;
import com.ael.algoryqrservice.store.service.StorefrontService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/public")
@RequiredArgsConstructor
public class StorefrontController {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final StorefrontService storefrontService;

    @GetMapping("/{storeNo}/{token}")
    public ResponseEntity<StorePublicDtos.StorefrontResponse> getStorefront(
            @PathVariable Long storeNo,
            @PathVariable String token
    ) {
        return ResponseEntity.ok(storefrontService.getStorefront(storeNo, token));
    }

    @PostMapping("/{storeNo}/{token}/orders")
    public ResponseEntity<StorePublicDtos.OrderTrackingResponse> placeOrder(
            @PathVariable Long storeNo,
            @PathVariable String token,
            @Valid @RequestBody StorePublicDtos.CreateOrderRequest request,
            HttpServletRequest httpRequest
    ) {
        StorePublicDtos.OrderTrackingResponse response =
                storefrontService.placeOrder(storeNo, token, request, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderToken}")
    public ResponseEntity<StorePublicDtos.OrderTrackingResponse> trackOrder(@PathVariable String orderToken) {
        return ResponseEntity.ok(storefrontService.trackOrder(orderToken));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        return forwarded.split(",")[0].trim();
    }
}
