package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.MenuOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu/{menuId}/orders")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final MenuOrderService menuOrderService;

    @GetMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listOrders(
            @PathVariable Long menuId,
            @RequestParam(required = false, defaultValue = "SUBMITTED") String status
    ) {
        return ResponseEntity.ok(menuOrderService.merchantList(menuId, status));
    }

    @PostMapping("/{orderId}/confirm")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuOrderDtos.OrderResponse> confirmOrder(
            @PathVariable Long menuId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(menuOrderService.merchantConfirm(menuId, orderId));
    }

    @PostMapping("/{orderId}/reject")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuOrderDtos.OrderResponse> rejectOrder(
            @PathVariable Long menuId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(menuOrderService.merchantReject(menuId, orderId));
    }
}
