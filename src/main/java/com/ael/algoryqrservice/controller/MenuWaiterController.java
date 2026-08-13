package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.MenuWaiterService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/menu/{menuId}/waiters")
@RequiredArgsConstructor
public class MenuWaiterController {

    private final MenuWaiterService menuWaiterService;

    @GetMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuWaiterDtos.UsersPageResponse> listWaiters(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuWaiterService.listWaiters(menuId));
    }

    @PostMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuWaiterDtos.WaiterResponse> createWaiter(
            @PathVariable Long menuId,
            @Valid @RequestBody MenuWaiterDtos.CreateWaiterRequest request
    ) {
        return ResponseEntity.status(201).body(menuWaiterService.createWaiter(menuId, request));
    }

    @PatchMapping("/{waiterId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<MenuWaiterDtos.WaiterResponse> updateWaiter(
            @PathVariable Long menuId,
            @PathVariable Long waiterId,
            @RequestBody MenuWaiterDtos.UpdateWaiterRequest request
    ) {
        return ResponseEntity.ok(menuWaiterService.updateWaiter(menuId, waiterId, request));
    }

    @DeleteMapping("/{waiterId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<Void> deleteWaiter(
            @PathVariable Long menuId,
            @PathVariable Long waiterId
    ) {
        menuWaiterService.deleteWaiter(menuId, waiterId);
        return ResponseEntity.noContent().build();
    }
}
