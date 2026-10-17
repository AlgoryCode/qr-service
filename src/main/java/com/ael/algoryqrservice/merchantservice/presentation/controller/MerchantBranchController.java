package com.ael.algoryqrservice.merchantservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.menu.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.qrservice.order.service.MenuWaiterOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Merchant → branch operational data for staff tokens ({@code merchantId} + {@code branchId} in JWT).
 */
@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantBranchController {

    private final MenuWaiterOrderService menuWaiterOrderService;

    @GetMapping("/{merchantId}/branches/{branchId}/tables")
    public ResponseEntity<List<MenuWaiterDtos.TableOrderSummary>> listBranchTables(
            @PathVariable Long merchantId,
            @PathVariable Long branchId
    ) {
        return ResponseEntity.ok(menuWaiterOrderService.listTablesForMerchantBranch(merchantId, branchId));
    }
}
