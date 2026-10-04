package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.service.MenuOrderService;
import com.ael.algoryqrservice.service.MenuService;
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
    private final MenuService menuService;

    @GetMapping("/{publicId}/cart")
    public ResponseEntity<MenuOrderDtos.OrderResponse> getCart(
            @PathVariable String publicId,
            @RequestHeader(TableSessionService.TABLE_SESSION_HEADER) String tableSessionToken
    ) {
        Menu menu = menuService.requireActivePublicMenu(publicId);
        return ResponseEntity.ok(menuOrderService.getCart(menu.getMenuId(), tableSessionToken));
    }

    @PutMapping("/{publicId}/cart")
    public ResponseEntity<MenuOrderDtos.OrderResponse> updateCart(
            @PathVariable String publicId,
            @RequestHeader(TableSessionService.TABLE_SESSION_HEADER) String tableSessionToken,
            @Valid @RequestBody MenuOrderDtos.UpdateCartRequest request
    ) {
        Menu menu = menuService.requireActivePublicMenu(publicId);
        return ResponseEntity.ok(menuOrderService.upsertCart(menu.getMenuId(), tableSessionToken, request));
    }

    @PostMapping("/{publicId}/orders/submit")
    public ResponseEntity<MenuOrderDtos.OrderResponse> submitOrder(
            @PathVariable String publicId,
            @RequestHeader(TableSessionService.TABLE_SESSION_HEADER) String tableSessionToken,
            @RequestBody(required = false) MenuOrderDtos.SubmitOrderRequest request
    ) {
        Menu menu = menuService.requireActivePublicMenu(publicId);
        java.util.UUID sessionId = request == null ? null : request.getAnalyticsSessionId();
        return ResponseEntity.ok(menuOrderService.submit(menu.getMenuId(), tableSessionToken, sessionId));
    }

    @GetMapping("/{publicId}/orders/{orderId}")
    public ResponseEntity<MenuOrderDtos.OrderResponse> getOrder(
            @PathVariable String publicId,
            @PathVariable Long orderId,
            @RequestHeader(TableSessionService.TABLE_SESSION_HEADER) String tableSessionToken
    ) {
        Menu menu = menuService.requireActivePublicMenu(publicId);
        return ResponseEntity.ok(menuOrderService.getOrder(menu.getMenuId(), tableSessionToken, orderId));
    }
}
