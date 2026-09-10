package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.dto.AdminSubscriptionDtos;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.service.PurchaseService;
import com.ael.algoryqrservice.service.RepairFulfillmentJob;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/admin/purchases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPurchaseController {

    private final PurchaseService purchaseService;
    private final RepairFulfillmentJob repairFulfillmentJob;

    @GetMapping("/{purchaseId}")
    public ResponseEntity<PurchaseSummaryResponse> getPurchase(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.getPurchaseSummaryAdmin(purchaseId));
    }

    @PatchMapping("/{purchaseId}")
    public ResponseEntity<PurchaseResponse> updatePurchase(
            @PathVariable Long purchaseId,
            @Valid @RequestBody AdminSubscriptionDtos.PurchaseUpdateRequest request
    ) {
        if (request.getStatus() != PurchaseStatus.EXPIRED) {
            throw new BadRequestException("Yalnızca EXPIRED durumuna geçiş desteklenir");
        }
        return ResponseEntity.ok(purchaseService.expirePurchase(purchaseId));
    }

    @PatchMapping("/{purchaseId}/subscription")
    public ResponseEntity<PurchaseResponse> updateSubscription(
            @PathVariable Long purchaseId,
            @Valid @RequestBody AdminSubscriptionDtos.SubscriptionUpdateRequest request
    ) {
        if (request.getDays() != null) {
            return ResponseEntity.ok(purchaseService.extendSubscriptionForAdmin(purchaseId, request.getDays()));
        }
        if (request.getStatus() != null && "INACTIVE".equals(request.getStatus().toUpperCase(Locale.ROOT))) {
            return ResponseEntity.ok(purchaseService.deactivateSubscriptionForAdmin(purchaseId));
        }
        throw new BadRequestException("days veya status=INACTIVE gerekli");
    }

    @PostMapping("/{purchaseId}/fulfillment-repairs")
    public ResponseEntity<Map<String, String>> repairFulfillment(@PathVariable Long purchaseId) {
        repairFulfillmentJob.repairForPurchase(purchaseId);
        return ResponseEntity.ok(Map.of("status", "ok", "purchaseId", String.valueOf(purchaseId)));
    }
}
