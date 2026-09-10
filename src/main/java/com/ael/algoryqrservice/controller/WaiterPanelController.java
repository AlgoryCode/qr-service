package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.model.dto.RestaurantTableDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.MenuOrderService;
import com.ael.algoryqrservice.service.MenuWaiterService;
import com.ael.algoryqrservice.service.MerchantCustomerService;
import com.ael.algoryqrservice.service.RestaurantTableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/waiter-panel")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.WAITER_PANEL_OWNER)
public class WaiterPanelController {

    private final MenuWaiterService menuWaiterService;
    private final RestaurantTableService restaurantTableService;
    private final MenuOrderService menuOrderService;
    private final MerchantCustomerService merchantCustomerService;

    @GetMapping("/customers/my")
    public ResponseEntity<List<MenuWaiterDtos.CustomerListItem>> listMyCustomers() {
        return ResponseEntity.ok(merchantCustomerService.listCustomersForCurrentBusiness());
    }

    @GetMapping("/branch/{branchId}/waiters")
    public ResponseEntity<MenuWaiterDtos.UsersPageResponse> listWaiters(@PathVariable Long branchId) {
        return ResponseEntity.ok(menuWaiterService.listWaiters(branchId));
    }

    @PostMapping("/branch/{branchId}/waiters")
    public ResponseEntity<MenuWaiterDtos.WaiterResponse> createWaiter(
            @PathVariable Long branchId,
            @Valid @RequestBody MenuWaiterDtos.CreateWaiterRequest request
    ) {
        return ResponseEntity.status(201).body(menuWaiterService.createWaiter(branchId, request));
    }

    @PatchMapping("/branch/{branchId}/waiters/{waiterId}")
    public ResponseEntity<MenuWaiterDtos.WaiterResponse> updateWaiter(
            @PathVariable Long branchId,
            @PathVariable Long waiterId,
            @RequestBody MenuWaiterDtos.UpdateWaiterRequest request
    ) {
        return ResponseEntity.ok(menuWaiterService.updateWaiter(branchId, waiterId, request));
    }

    @DeleteMapping("/branch/{branchId}/waiters/{waiterId}")
    public ResponseEntity<Void> deleteWaiter(
            @PathVariable Long branchId,
            @PathVariable Long waiterId
    ) {
        menuWaiterService.deleteWaiter(branchId, waiterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/menu/{menuId}/tables")
    public ResponseEntity<List<RestaurantTableDtos.TableResponse>> listTables(@PathVariable Long menuId) {
        return ResponseEntity.ok(restaurantTableService.listTables(menuId));
    }

    @PostMapping("/menu/{menuId}/tables")
    public ResponseEntity<RestaurantTableDtos.TableResponse> createTable(
            @PathVariable Long menuId,
            @Valid @RequestBody RestaurantTableDtos.CreateTableRequest request
    ) {
        return ResponseEntity.status(201).body(restaurantTableService.createTable(menuId, request));
    }

    @PatchMapping("/menu/{menuId}/tables/{tableId}")
    public ResponseEntity<RestaurantTableDtos.TableResponse> updateTable(
            @PathVariable Long menuId,
            @PathVariable Long tableId,
            @RequestBody RestaurantTableDtos.UpdateTableRequest request
    ) {
        return ResponseEntity.ok(restaurantTableService.updateTable(menuId, tableId, request));
    }

    @PostMapping("/menu/{menuId}/tables/{tableId}/regenerate-qr")
    public ResponseEntity<RestaurantTableDtos.TableResponse> regenerateQr(
            @PathVariable Long menuId,
            @PathVariable Long tableId
    ) {
        return ResponseEntity.ok(restaurantTableService.regenerateQr(menuId, tableId));
    }

    @DeleteMapping("/menu/{menuId}/tables/{tableId}")
    public ResponseEntity<Void> deleteTable(
            @PathVariable Long menuId,
            @PathVariable Long tableId
    ) {
        restaurantTableService.deleteTable(menuId, tableId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/menu/{menuId}/orders")
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listOrders(
            @PathVariable Long menuId,
            @RequestParam(required = false, defaultValue = "ALL") String status
    ) {
        return ResponseEntity.ok(menuOrderService.merchantList(menuId, status));
    }

    @GetMapping("/menu/{menuId}/orders/{orderId}")
    public ResponseEntity<MenuOrderDtos.OrderResponse> getOrder(
            @PathVariable Long menuId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(menuOrderService.merchantGet(menuId, orderId));
    }

    @PostMapping("/menu/{menuId}/orders/{orderId}/cancel")
    public ResponseEntity<MenuOrderDtos.OrderResponse> cancelOrder(
            @PathVariable Long menuId,
            @PathVariable Long orderId,
            @RequestBody(required = false) MenuOrderDtos.CancelOrderRequest request
    ) {
        return ResponseEntity.ok(menuOrderService.merchantCancel(menuId, orderId, request));
    }

    @GetMapping("/menu/{menuId}/customers")
    public ResponseEntity<List<MenuWaiterDtos.CustomerListItem>> listCustomers(@PathVariable Long menuId) {
        return ResponseEntity.ok(merchantCustomerService.listCustomers(menuId));
    }
}
