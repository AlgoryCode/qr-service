package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.*;
import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.PurchaseService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchaseLogService purchaseLogService;
    private final EntitlementService entitlementService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<PurchaseResponse> purchase(@Valid @RequestBody PurchaseRequest request) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.purchase(userId, request));
    }

    @GetMapping("/my")
    public ResponseEntity<List<PurchaseResponse>> getMyPurchases() {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.getUserPurchases(userId));
    }

    @GetMapping("/my/logs")
    public ResponseEntity<List<PurchaseLogResponse>> getMyPurchaseLogs() {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseLogService.getUserLogs(userId));
    }

    @GetMapping("/{purchaseId}/summary")
    public ResponseEntity<PurchaseSummaryResponse> getPurchaseSummary(@PathVariable Long purchaseId) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.getPurchaseSummary(purchaseId, userId));
    }

    @GetMapping("/{purchaseId}/logs")
    public ResponseEntity<List<PurchaseLogResponse>> getPurchaseLogs(@PathVariable Long purchaseId) {
        Long userId = securityUtils.getCurrentUser().getId();
        purchaseService.findUserPurchase(purchaseId, userId);
        return ResponseEntity.ok(purchaseLogService.getPurchaseLogs(purchaseId));
    }

    @GetMapping("/my/entitlements")
    public ResponseEntity<List<UserEntitlementResponse>> getMyEntitlements() {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(entitlementService.getUserEntitlements(userId));
    }
}
