package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/purchases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping("/{purchaseId}/expire")
    public ResponseEntity<PurchaseResponse> expirePurchase(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.expirePurchase(purchaseId));
    }

    @GetMapping("/{purchaseId}/summary")
    public ResponseEntity<PurchaseSummaryResponse> getPurchaseSummary(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.getPurchaseSummaryAdmin(purchaseId));
    }
}
