package com.ael.algoryqrservice.merchantservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.customerservice.application.MenuCustomerListService;
import com.ael.algoryqrservice.qrservice.menu.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.qrservice.security.RequiresProductScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/merchant/menus")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.WAITER_PANEL_OWNER)
public class MerchantMenuCustomerController {

    private final MenuCustomerListService menuCustomerListService;

    @GetMapping("/{menuId}/customers")
    public ResponseEntity<List<MenuWaiterDtos.CustomerListItem>> listCustomers(@PathVariable Long menuId) {
        return ResponseEntity.ok(menuCustomerListService.listCustomers(menuId));
    }
}
