package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.service.MenuOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final MenuOrderService menuOrderService;

    @GetMapping
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listOrders(
            @RequestParam Long menuId
    ) {
        return ResponseEntity.ok(menuOrderService.customerList(menuId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<MenuOrderDtos.OrderResponse> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(menuOrderService.customerGet(orderId));
    }
}
