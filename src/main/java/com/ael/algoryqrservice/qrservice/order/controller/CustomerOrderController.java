package com.ael.algoryqrservice.qrservice.order.controller;

import com.ael.algoryqrservice.qrservice.order.dto.MenuOrderDtos;
import com.ael.algoryqrservice.qrservice.order.service.MenuOrderService;
import com.ael.algoryqrservice.qrservice.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final MenuOrderService menuOrderService;
    private final MenuService menuService;

    @GetMapping
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listOrders(
            @RequestParam String publicId
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        return ResponseEntity.ok(menuOrderService.customerList(menuId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<MenuOrderDtos.OrderResponse> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(menuOrderService.customerGet(orderId));
    }
}
