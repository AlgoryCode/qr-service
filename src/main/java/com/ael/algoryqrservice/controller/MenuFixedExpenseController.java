package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.MenuFixedExpenseDtos;
import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.service.MenuFixedExpenseService;
import com.ael.algoryqrservice.util.SecurityUtils;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/menus/{menuId}/fixed-expenses")
@RequiredArgsConstructor
public class MenuFixedExpenseController {

    private final MenuFixedExpenseService menuFixedExpenseService;
    private final EntitlementService entitlementService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<List<MenuFixedExpenseDtos.Response>> list(@PathVariable Long menuId) {
        requireOrderAnalyticsScope();
        return ResponseEntity.ok(menuFixedExpenseService.list(menuId));
    }

    @PostMapping
    public ResponseEntity<MenuFixedExpenseDtos.Response> create(
            @PathVariable Long menuId,
            @Valid @RequestBody MenuFixedExpenseDtos.CreateRequest request
    ) {
        requireOrderAnalyticsScope();
        return ResponseEntity.ok(menuFixedExpenseService.create(menuId, request));
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<MenuFixedExpenseDtos.Response> update(
            @PathVariable Long menuId,
            @PathVariable Long expenseId,
            @Valid @RequestBody MenuFixedExpenseDtos.UpdateRequest request
    ) {
        requireOrderAnalyticsScope();
        return ResponseEntity.ok(menuFixedExpenseService.update(menuId, expenseId, request));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long menuId,
            @PathVariable Long expenseId
    ) {
        requireOrderAnalyticsScope();
        menuFixedExpenseService.delete(menuId, expenseId);
        return ResponseEntity.noContent().build();
    }

    private void requireOrderAnalyticsScope() {
        Long userId = securityUtils.getCurrentUserId();
        if (entitlementService.hasScope(userId, CatalogScopes.SMART_REPORTING_OWNER)
                || entitlementService.hasScope(userId, CatalogScopes.WAITER_PANEL_OWNER)) {
            return;
        }
        entitlementService.requireScope(userId, CatalogScopes.SMART_REPORTING_OWNER);
    }
}
