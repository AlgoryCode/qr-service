package com.ael.algoryqrservice.integration.yemeksepeti.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.ael.algoryqrservice.integration.yemeksepeti.service.YemekSepetiConnectionService;
import com.ael.algoryqrservice.integration.yemeksepeti.service.YemekSepetiOrderService;
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
@RequestMapping("/integrations/yemek-sepeti")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
public class YemekSepetiController {

    private final YemekSepetiConnectionService connectionService;
    private final YemekSepetiOrderService orderService;

    @GetMapping("/connections")
    public ResponseEntity<List<YemekSepetiDtos.ConnectionResponse>> listConnections() {
        return ResponseEntity.ok(connectionService.listMine());
    }

    @GetMapping("/connections/me")
    public ResponseEntity<YemekSepetiDtos.ConnectionResponse> getConnection() {
        return ResponseEntity.ok(connectionService.getMine());
    }

    @PutMapping("/connections")
    public ResponseEntity<YemekSepetiDtos.ConnectionResponse> upsert(
            @Valid @RequestBody YemekSepetiDtos.UpsertConnectionRequest request
    ) {
        return ResponseEntity.ok(connectionService.upsert(request));
    }

    @DeleteMapping("/connections/me")
    public ResponseEntity<YemekSepetiDtos.ConnectionResponse> disconnect() {
        return ResponseEntity.ok(connectionService.disconnect());
    }

    @GetMapping("/restaurants")
    public ResponseEntity<List<YemekSepetiDtos.RestaurantResponse>> restaurants() {
        YemekSepetiDtos.ConnectionResponse connection = connectionService.getMine();
        if (connection.getVendorId() == null || connection.getVendorId().isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(List.of(YemekSepetiDtos.RestaurantResponse.builder()
                .id(connection.getVendorId())
                .name(connection.getVendorName())
                .build()));
    }

    @GetMapping("/orders")
    public ResponseEntity<YemekSepetiDtos.OrderPageResponse> orders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(orderService.listOrders(status, from, to, page, size));
    }

    @PostMapping("/orders/sync")
    public ResponseEntity<YemekSepetiDtos.SyncOrdersResponse> syncOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(orderService.syncOrders(from, to));
    }

    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<YemekSepetiDtos.OrderResponse> accept(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.accept(orderId));
    }

    @PostMapping("/orders/{orderId}/reject")
    public ResponseEntity<YemekSepetiDtos.OrderResponse> reject(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.reject(orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<YemekSepetiDtos.OrderResponse> cancel(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancel(orderId));
    }

    @PostMapping("/orders/{orderId}/ready")
    public ResponseEntity<YemekSepetiDtos.OrderResponse> ready(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markReady(orderId));
    }
}
