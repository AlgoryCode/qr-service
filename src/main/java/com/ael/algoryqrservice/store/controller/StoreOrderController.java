package com.ael.algoryqrservice.store.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.model.dto.StoreOrderDtos;
import com.ael.algoryqrservice.store.service.StoreOrderPanelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/store/orders")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.ONLINE_ORDER_OWNER)
public class StoreOrderController {

    private final StoreOrderPanelService storeOrderPanelService;

    @GetMapping
    public ResponseEntity<StoreOrderDtos.OrderPage> list(
            @RequestParam(required = false) List<StoreOrderStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(storeOrderPanelService.list(status, from, to, page, size));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<StoreOrderDtos.OrderDetail> get(@PathVariable Long orderId) {
        return ResponseEntity.ok(storeOrderPanelService.get(orderId));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<StoreOrderDtos.OrderDetail> confirm(@PathVariable Long orderId) {
        return ResponseEntity.ok(storeOrderPanelService.advance(orderId, StoreOrderStatus.CONFIRMED, null));
    }

    @PostMapping("/{orderId}/preparing")
    public ResponseEntity<StoreOrderDtos.OrderDetail> preparing(@PathVariable Long orderId) {
        return ResponseEntity.ok(storeOrderPanelService.advance(orderId, StoreOrderStatus.PREPARING, null));
    }

    @PostMapping("/{orderId}/ready")
    public ResponseEntity<StoreOrderDtos.OrderDetail> ready(@PathVariable Long orderId) {
        return ResponseEntity.ok(storeOrderPanelService.advance(orderId, StoreOrderStatus.READY, null));
    }

    @PostMapping("/{orderId}/dispatch")
    public ResponseEntity<StoreOrderDtos.OrderDetail> dispatch(@PathVariable Long orderId) {
        return ResponseEntity.ok(storeOrderPanelService.advance(orderId, StoreOrderStatus.ON_THE_WAY, null));
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<StoreOrderDtos.OrderDetail> deliver(@PathVariable Long orderId) {
        return ResponseEntity.ok(storeOrderPanelService.advance(orderId, StoreOrderStatus.DELIVERED, null));
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<StoreOrderDtos.OrderDetail> reject(
            @PathVariable Long orderId,
            @Valid @RequestBody StoreOrderDtos.TransitionRequest request
    ) {
        return ResponseEntity.ok(storeOrderPanelService.advance(orderId, StoreOrderStatus.REJECTED, request.reason()));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<StoreOrderDtos.OrderDetail> cancel(
            @PathVariable Long orderId,
            @Valid @RequestBody StoreOrderDtos.TransitionRequest request
    ) {
        return ResponseEntity.ok(storeOrderPanelService.advance(orderId, StoreOrderStatus.CANCELLED, request.reason()));
    }

    @PostMapping("/{orderId}/courier")
    public ResponseEntity<StoreOrderDtos.OrderDetail> assignCourier(
            @PathVariable Long orderId,
            @Valid @RequestBody StoreOrderDtos.AssignCourierRequest request
    ) {
        return ResponseEntity.ok(storeOrderPanelService.assignCourier(orderId, request.courierId()));
    }
}
