package com.ael.algoryqrservice.integration.ubereats.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsConnectionService;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsMenuQueryService;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsOrderService;
import com.ael.algoryqrservice.security.RequiresProductScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/integrations/ubereats")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
public class UberEatsController {

    private final UberEatsConnectionService connectionService;
    private final UberEatsMenuQueryService menuQueryService;
    private final UberEatsOrderService orderService;

    @GetMapping("/connections")
    public ResponseEntity<List<UberEatsDtos.ConnectionResponse>> listConnections() {
        return ResponseEntity.ok(connectionService.listMine());
    }

    @GetMapping("/connections/me")
    public ResponseEntity<UberEatsDtos.ConnectionResponse> getConnection() {
        return ResponseEntity.ok(connectionService.getMine());
    }

    @PutMapping("/connections")
    public ResponseEntity<UberEatsDtos.ConnectionResponse> upsert(
            @Valid @RequestBody UberEatsDtos.UpsertConnectionRequest request
    ) {
        return ResponseEntity.ok(connectionService.upsert(request));
    }

    @DeleteMapping("/connections/me")
    public ResponseEntity<UberEatsDtos.ConnectionResponse> disconnect() {
        return ResponseEntity.ok(connectionService.disconnect());
    }

    @GetMapping("/restaurants")
    public ResponseEntity<List<UberEatsDtos.RestaurantResponse>> restaurants() {
        return ResponseEntity.ok(connectionService.listRestaurants());
    }

    @GetMapping("/products")
    public ResponseEntity<UberEatsDtos.ProductPageResponse> products(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(menuQueryService.listProducts(q, page, size));
    }

    @GetMapping("/orders")
    public ResponseEntity<UberEatsDtos.OrderPageResponse> orders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(orderService.listOrders(status, from, to, page, size));
    }

    @PostMapping("/orders/sync")
    public ResponseEntity<UberEatsDtos.SyncOrdersResponse> syncOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(orderService.syncOrders(from, to));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<UberEatsDtos.OrderResponse> order(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<UberEatsDtos.OrderResponse> accept(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.accept(orderId));
    }

    @PostMapping("/orders/{orderId}/reject")
    public ResponseEntity<UberEatsDtos.OrderResponse> reject(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.reject(orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<UberEatsDtos.OrderResponse> cancel(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancel(orderId));
    }

    @PostMapping("/orders/{orderId}/ready")
    public ResponseEntity<UberEatsDtos.OrderResponse> ready(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markReady(orderId));
    }
}
