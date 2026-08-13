package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.service.MenuWaiterOrderService;
import com.ael.algoryqrservice.util.SecurityUtils;
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
    private final SecurityUtils securityUtils;

    @GetMapping("/pending")
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listPending(
            @RequestParam(required = false) Long menuId
    ) {
        Long resolvedMenuId = menuId != null ? menuId : securityUtils.getCurrentWaiterMenuId();
        return ResponseEntity.ok(menuWaiterOrderService.listPending(resolvedMenuId));
    }

    @GetMapping("/today")
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listToday(
            @RequestParam(required = false) Long menuId
    ) {
        Long resolvedMenuId = menuId != null ? menuId : securityUtils.getCurrentWaiterMenuId();
        return ResponseEntity.ok(menuWaiterOrderService.listTodayHistory(resolvedMenuId));
    }

    @GetMapping("/catalog")
    public ResponseEntity<MenuWaiterDtos.CatalogResponse> listCatalog() {
        return ResponseEntity.ok(menuWaiterOrderService.listCatalog());
    }

    @PostMapping
    public ResponseEntity<MenuOrderDtos.OrderResponse> create(
            @Valid @RequestBody MenuOrderDtos.WaiterCreateOrderRequest request
    ) {
        return ResponseEntity.ok(menuWaiterOrderService.createOrder(request));
    }

    @GetMapping("/tables")
    public ResponseEntity<List<MenuWaiterDtos.TableOrderSummary>> listTables(
            @RequestParam(required = false) Long menuId
    ) {
        Long resolvedMenuId = menuId != null ? menuId : securityUtils.getCurrentWaiterMenuId();
        return ResponseEntity.ok(menuWaiterOrderService.listTables(resolvedMenuId));
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
