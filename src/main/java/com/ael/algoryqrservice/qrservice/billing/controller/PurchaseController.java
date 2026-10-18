package com.ael.algoryqrservice.qrservice.billing.controller;

import com.ael.algoryqrservice.qrservice.model.dto.*;
import com.ael.algoryqrservice.qrservice.billing.service.AddonPurchaseService;
import com.ael.algoryqrservice.qrservice.billing.service.ExternalPackageViewService;
import com.ael.algoryqrservice.qrservice.billing.service.PurchaseLogService;
import com.ael.algoryqrservice.qrservice.billing.service.PurchaseService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.ael.algoryqrservice.qrservice.billing.dto.AddonCartPurchaseRequest;
import com.ael.algoryqrservice.qrservice.billing.dto.AddonPurchaseRequest;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseFulfillmentResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseLogResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseRequest;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.SubscriptionOverviewResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.UserEntitlementResponse;

@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final AddonPurchaseService addonPurchaseService;
    private final PurchaseLogService purchaseLogService;
    private final ExternalPackageViewService externalPackageView;
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

    @PostMapping("/addons/cart")
    public ResponseEntity<PurchaseInitiateResponse> purchaseAddonCart(
            @Valid @RequestBody AddonCartPurchaseRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = resolveClientIp(httpServletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                addonPurchaseService.purchaseCart(securityUtils.getCurrentUser(), request, clientIp)
        );
    }

    @GetMapping("/my")
    public ResponseEntity<List<PurchaseResponse>> getMyPurchases() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseService.getUserPurchases(merchantId));
    }

    @GetMapping("/my/subscription-overview")
    public ResponseEntity<SubscriptionOverviewResponse> getMySubscriptionOverview() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseService.getMySubscriptionOverview(merchantId));
    }

    @GetMapping("/my/entitlements")
    public ResponseEntity<List<UserEntitlementResponse>> getMyEntitlements() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(externalPackageView.entitlements(merchantId).orElseGet(List::of));
    }

    @GetMapping("/my/logs")
    public ResponseEntity<List<PurchaseLogResponse>> getMyPurchaseLogs() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseLogService.getUserLogs(merchantId));
    }

    @GetMapping("/{purchaseId}/summary")
    public ResponseEntity<PurchaseSummaryResponse> getPurchaseSummary(@PathVariable Long purchaseId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseService.getPurchaseSummary(purchaseId, merchantId));
    }

    @PostMapping("/{purchaseId}/cancel")
    public ResponseEntity<PurchaseResponse> cancelMyPurchase(@PathVariable Long purchaseId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseService.cancelMyPurchase(purchaseId, merchantId));
    }

    @PostMapping("/{purchaseId}/cancel-at-period-end")
    public ResponseEntity<PurchaseResponse> cancelAtPeriodEnd(@PathVariable Long purchaseId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseService.cancelAtPeriodEnd(purchaseId, merchantId));
    }

    @PostMapping("/{purchaseId}/resume-renewal")
    public ResponseEntity<PurchaseResponse> resumeRenewal(@PathVariable Long purchaseId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseService.resumeRenewal(purchaseId, merchantId));
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
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseService.cancelWithRefund(
                purchaseId,
                merchantId,
                resolveClientIp(httpServletRequest)
        ));
    }

    @GetMapping("/{purchaseId}/installments")
    public ResponseEntity<List<PurchaseFulfillmentResponse>> getPurchaseInstallments(@PathVariable Long purchaseId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(purchaseService.getPurchaseInstallments(purchaseId, merchantId));
    }

    @GetMapping("/{purchaseId}/logs")
    public ResponseEntity<List<PurchaseLogResponse>> getPurchaseLogs(@PathVariable Long purchaseId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        purchaseService.findUserPurchase(purchaseId, merchantId);
        return ResponseEntity.ok(purchaseLogService.getPurchaseLogs(purchaseId));
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
