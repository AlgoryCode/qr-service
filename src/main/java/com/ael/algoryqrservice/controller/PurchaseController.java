package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.*;
import com.ael.algoryqrservice.service.AddonPurchaseService;
import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.PurchaseService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AddonPurchaseService addonPurchaseService;
    private final PurchaseLogService purchaseLogService;
    private final EntitlementService entitlementService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<PurchaseInitiateResponse> purchase(
            @Valid @RequestBody PurchaseRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = resolveClientIp(httpServletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                purchaseService.purchase(securityUtils.getCurrentUser(), request, clientIp)
        );
    }

    @PostMapping("/addons")
    public ResponseEntity<PurchaseInitiateResponse> purchaseAddon(
            @Valid @RequestBody AddonPurchaseRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = resolveClientIp(httpServletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                addonPurchaseService.purchase(securityUtils.getCurrentUser(), request, clientIp)
        );
    }

    @GetMapping("/my")
    public ResponseEntity<List<PurchaseResponse>> getMyPurchases() {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.getUserPurchases(userId));
    }

    @GetMapping("/my/subscription-overview")
    public ResponseEntity<SubscriptionOverviewResponse> getMySubscriptionOverview() {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.getMySubscriptionOverview(userId));
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

    @PostMapping("/{purchaseId}/cancel")
    public ResponseEntity<PurchaseResponse> cancelMyPurchase(@PathVariable Long purchaseId) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.cancelMyPurchase(purchaseId, userId));
    }

    @PostMapping("/{purchaseId}/cancel-at-period-end")
    public ResponseEntity<PurchaseResponse> cancelAtPeriodEnd(@PathVariable Long purchaseId) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.cancelAtPeriodEnd(purchaseId, userId));
    }

    @PostMapping("/{purchaseId}/resume-renewal")
    public ResponseEntity<PurchaseResponse> resumeRenewal(@PathVariable Long purchaseId) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.resumeRenewal(purchaseId, userId));
    }

    @PostMapping("/{purchaseId}/pay-debt")
    public ResponseEntity<PurchaseInitiateResponse> paySubscriptionDebt(
            @PathVariable Long purchaseId,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = resolveClientIp(httpServletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                purchaseService.paySubscriptionDebt(securityUtils.getCurrentUser(), purchaseId, clientIp)
        );
    }

    @PostMapping("/{purchaseId}/cancel-with-refund")
    public ResponseEntity<PurchaseResponse> cancelWithRefund(
            @PathVariable Long purchaseId,
            HttpServletRequest httpServletRequest
    ) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.cancelWithRefund(
                purchaseId,
                userId,
                resolveClientIp(httpServletRequest)
        ));
    }

    @GetMapping("/{purchaseId}/installments")
    public ResponseEntity<List<PurchaseFulfillmentResponse>> getPurchaseInstallments(@PathVariable Long purchaseId) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(purchaseService.getPurchaseInstallments(purchaseId, userId));
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

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            for (String candidate : forwardedFor.split(",")) {
                String ip = candidate.trim();
                if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                    return ip;
                }
            }
            return forwardedFor.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "127.0.0.1" : remote;
    }
}
