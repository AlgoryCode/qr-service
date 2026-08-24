package com.ael.algoryqrservice.integration.trendyolgo.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.integration.trendyolgo.model.dto.TrendyolGoDtos;
import com.ael.algoryqrservice.integration.trendyolgo.service.TrendyolGoConnectionService;
import com.ael.algoryqrservice.integration.trendyolgo.service.TrendyolGoMenuQueryService;
import com.ael.algoryqrservice.integration.trendyolgo.service.TrendyolGoOrderService;
import com.ael.algoryqrservice.security.RequiresProductScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/integrations/trendyol-go")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
public class TrendyolGoController {

    private final TrendyolGoConnectionService connectionService;
    private final TrendyolGoMenuQueryService menuQueryService;
    private final TrendyolGoOrderService orderService;

    @GetMapping("/connections")
    public ResponseEntity<List<TrendyolGoDtos.ConnectionResponse>> listConnections() {
        return ResponseEntity.ok(connectionService.listMine());
    }

    @GetMapping("/connections/{branchId}")
    public ResponseEntity<TrendyolGoDtos.ConnectionResponse> getConnection(@PathVariable Long branchId) {
        return ResponseEntity.ok(connectionService.getMine(branchId));
    }

    @PutMapping("/connections")
    public ResponseEntity<TrendyolGoDtos.ConnectionResponse> upsert(
            @Valid @RequestBody TrendyolGoDtos.UpsertConnectionRequest request
    ) {
        return ResponseEntity.ok(connectionService.upsert(request));
    }

    @DeleteMapping("/connections/{branchId}")
    public ResponseEntity<TrendyolGoDtos.ConnectionResponse> disconnect(@PathVariable Long branchId) {
        return ResponseEntity.ok(connectionService.disconnect(branchId));
    }

    @GetMapping("/restaurants")
    public ResponseEntity<List<TrendyolGoDtos.RestaurantResponse>> restaurants(@RequestParam Long branchId) {
        return ResponseEntity.ok(connectionService.listRestaurants(branchId));
    }

    @GetMapping("/products")
    public ResponseEntity<TrendyolGoDtos.ProductPageResponse> products(
            @RequestParam Long branchId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(menuQueryService.listProducts(branchId, q, page, size));
    }

    @GetMapping("/orders")
    public ResponseEntity<TrendyolGoDtos.OrderPageResponse> orders(
            @RequestParam Long branchId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(orderService.listOrders(branchId, status, page, size));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<TrendyolGoDtos.OrderResponse> order(
            @RequestParam Long branchId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.getOrder(branchId, orderId));
    }

    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<TrendyolGoDtos.OrderResponse> accept(
            @RequestParam Long branchId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.accept(branchId, orderId));
    }

    @PostMapping("/orders/{orderId}/reject")
    public ResponseEntity<TrendyolGoDtos.OrderResponse> reject(
            @RequestParam Long branchId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.reject(branchId, orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<TrendyolGoDtos.OrderResponse> cancel(
            @RequestParam Long branchId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.cancel(branchId, orderId));
    }

    @PostMapping("/orders/{orderId}/ready")
    public ResponseEntity<TrendyolGoDtos.OrderResponse> ready(
            @RequestParam Long branchId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.markReady(branchId, orderId));
    }
}
