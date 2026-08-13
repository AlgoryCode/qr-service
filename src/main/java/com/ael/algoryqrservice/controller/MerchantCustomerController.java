package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.MerchantCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/menu/{menuId}/customers")
@RequiredArgsConstructor
public class MerchantCustomerController {

    private final MerchantCustomerService merchantCustomerService;

    @GetMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<List<MenuWaiterDtos.CustomerListItem>> listCustomers(@PathVariable Long menuId) {
        return ResponseEntity.ok(merchantCustomerService.listCustomers(menuId));
    }
}
