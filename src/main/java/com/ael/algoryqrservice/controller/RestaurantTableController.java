package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.RestaurantTableDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.RestaurantTableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu/{menuId}/tables")
@RequiredArgsConstructor
public class RestaurantTableController {

    private final RestaurantTableService restaurantTableService;

    @GetMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<List<RestaurantTableDtos.TableResponse>> listTables(@PathVariable Long menuId) {
        return ResponseEntity.ok(restaurantTableService.listTables(menuId));
    }

    @PostMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<RestaurantTableDtos.TableResponse> createTable(
            @PathVariable Long menuId,
            @Valid @RequestBody RestaurantTableDtos.CreateTableRequest request
    ) {
        return ResponseEntity.status(201).body(restaurantTableService.createTable(menuId, request));
    }

    @PatchMapping("/{tableId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<RestaurantTableDtos.TableResponse> updateTable(
            @PathVariable Long menuId,
            @PathVariable Long tableId,
            @RequestBody RestaurantTableDtos.UpdateTableRequest request
    ) {
        return ResponseEntity.ok(restaurantTableService.updateTable(menuId, tableId, request));
    }

    @PostMapping("/{tableId}/regenerate-qr")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<RestaurantTableDtos.TableResponse> regenerateQr(
            @PathVariable Long menuId,
            @PathVariable Long tableId
    ) {
        return ResponseEntity.ok(restaurantTableService.regenerateQr(menuId, tableId));
    }

    @DeleteMapping("/{tableId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<Void> deleteTable(
            @PathVariable Long menuId,
            @PathVariable Long tableId
    ) {
        restaurantTableService.deleteTable(menuId, tableId);
        return ResponseEntity.noContent().build();
    }
}
