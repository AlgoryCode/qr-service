package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.service.MenuOrderService;
import com.ael.algoryqrservice.service.TableSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/menu/public")
@RequiredArgsConstructor
public class PublicOrderController {

    private final MenuOrderService menuOrderService;

    @GetMapping("/id/{qrId}/cart")
    public ResponseEntity<MenuOrderDtos.OrderResponse> getCart(
            @PathVariable Long qrId,
            @RequestHeader(TableSessionService.TABLE_SESSION_HEADER) String tableSessionToken
    ) {
        return ResponseEntity.ok(menuOrderService.getCart(qrId, tableSessionToken));
    }

    @PutMapping("/id/{qrId}/cart")
    public ResponseEntity<MenuOrderDtos.OrderResponse> updateCart(
            @PathVariable Long qrId,
            @RequestHeader(TableSessionService.TABLE_SESSION_HEADER) String tableSessionToken,
            @Valid @RequestBody MenuOrderDtos.UpdateCartRequest request
    ) {
        return ResponseEntity.ok(menuOrderService.upsertCart(qrId, tableSessionToken, request));
    }

    @PostMapping("/id/{qrId}/orders/submit")
    public ResponseEntity<MenuOrderDtos.OrderResponse> submitOrder(
            @PathVariable Long qrId,
            @RequestHeader(TableSessionService.TABLE_SESSION_HEADER) String tableSessionToken
    ) {
        return ResponseEntity.ok(menuOrderService.submit(qrId, tableSessionToken));
    }

    @GetMapping("/id/{qrId}/orders/{orderId}")
    public ResponseEntity<MenuOrderDtos.OrderResponse> getOrder(
            @PathVariable Long qrId,
            @PathVariable Long orderId,
            @RequestHeader(TableSessionService.TABLE_SESSION_HEADER) String tableSessionToken
    ) {
        return ResponseEntity.ok(menuOrderService.getOrder(qrId, tableSessionToken, orderId));
    }
}
