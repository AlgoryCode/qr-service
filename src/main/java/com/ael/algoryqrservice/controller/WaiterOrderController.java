package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.service.MenuWaiterOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/waiter/orders")
@RequiredArgsConstructor
public class WaiterOrderController {

    private final MenuWaiterOrderService menuWaiterOrderService;

    @GetMapping("/pending")
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listPending() {
        return ResponseEntity.ok(menuWaiterOrderService.listPending());
    }

    @GetMapping("/today")
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listToday() {
        return ResponseEntity.ok(menuWaiterOrderService.listTodayHistory());
    }

    @GetMapping("/catalog")
    public ResponseEntity<MenuWaiterDtos.CatalogResponse> listCatalog(@RequestParam Long tableId) {
        return ResponseEntity.ok(menuWaiterOrderService.listCatalog(tableId));
    }

    @PostMapping
    public ResponseEntity<MenuOrderDtos.OrderResponse> create(
            @Valid @RequestBody MenuOrderDtos.WaiterCreateOrderRequest request
    ) {
        return ResponseEntity.ok(menuWaiterOrderService.createOrder(request));
    }

    @GetMapping("/tables")
    public ResponseEntity<List<MenuWaiterDtos.TableOrderSummary>> listTables() {
        return ResponseEntity.ok(menuWaiterOrderService.listTables());
    }

    @GetMapping("/tables/{tableId}/today")
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> getTableTodayOrders(@PathVariable Long tableId) {
        return ResponseEntity.ok(menuWaiterOrderService.getTableTodayOrders(tableId));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<MenuOrderDtos.OrderResponse> confirm(@PathVariable Long orderId) {
        return ResponseEntity.ok(menuWaiterOrderService.confirm(orderId));
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<MenuOrderDtos.OrderResponse> reject(@PathVariable Long orderId) {
        return ResponseEntity.ok(menuWaiterOrderService.reject(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<MenuOrderDtos.OrderResponse> cancel(@PathVariable Long orderId) {
        return ResponseEntity.ok(menuWaiterOrderService.cancel(orderId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<MenuOrderDtos.OrderResponse> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(menuWaiterOrderService.getOrder(orderId));
    }

    @PatchMapping("/{orderId}/note")
    public ResponseEntity<MenuOrderDtos.OrderResponse> updateNote(
            @PathVariable Long orderId,
            @Valid @RequestBody MenuWaiterDtos.WaiterNoteRequest request
    ) {
        String note = request != null ? request.getNote() : null;
        return ResponseEntity.ok(menuWaiterOrderService.updateWaiterNote(orderId, note));
    }
}
