package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import com.ael.algoryqrservice.model.dto.FulfillmentDetailResponse;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.PurchaseSummaryResponse;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.service.FulfillmentMigrationService;
import com.ael.algoryqrservice.service.PurchaseService;
import com.ael.algoryqrservice.service.RepairFulfillmentJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/purchases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPurchaseController {

    private final PurchaseService purchaseService;
    private final RepairFulfillmentJob repairFulfillmentJob;
    private final FulfillmentMigrationService fulfillmentMigrationService;
    private final FulfillmentUsageLogRepository fulfillmentUsageLogRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;

    @PostMapping("/{purchaseId}/expire")
    public ResponseEntity<PurchaseResponse> expirePurchase(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.expirePurchase(purchaseId));
    }

    @GetMapping("/{purchaseId}/summary")
    public ResponseEntity<PurchaseSummaryResponse> getPurchaseSummary(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.getPurchaseSummaryAdmin(purchaseId));
    }

    @PostMapping("/{purchaseId}/repair-fulfillment")
    public ResponseEntity<Map<String, String>> repairFulfillment(@PathVariable Long purchaseId) {
        repairFulfillmentJob.repairForPurchase(purchaseId);
        return ResponseEntity.ok(Map.of("status", "ok", "purchaseId", String.valueOf(purchaseId)));
    }

    @PostMapping("/users/{userId}/backfill-fulfillment")
    public ResponseEntity<FulfillmentMigrationService.MigrationResult> backfillUser(@PathVariable Long userId) {
        return ResponseEntity.ok(fulfillmentMigrationService.backfillUser(userId));
    }

    @GetMapping("/users/{userId}/fulfillment-parity")
    public ResponseEntity<FulfillmentMigrationService.ParityReport> parityReport(@PathVariable Long userId) {
        return ResponseEntity.ok(fulfillmentMigrationService.parityReport(userId));
    }

    @GetMapping("/users/{userId}/usage-log")
    public ResponseEntity<Page<FulfillmentUsageLog>> usageLog(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(
                fulfillmentUsageLogRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
        );
    }

    @GetMapping("/users/{userId}/fulfillment-details")
    public ResponseEntity<List<FulfillmentDetailResponse>> fulfillmentDetails(@PathVariable Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<FulfillmentDetailResponse> details = fulfillmentDetailRepository
                .findAllActiveByUserId(userId, now).stream()
                .map(d -> FulfillmentDetailResponse.builder()
                        .id(d.getId())
                        .fulfillmentId(d.getFulfillmentId())
                        .featureCode(d.getFeatureCode())
                        .scopeCode(d.getScopeCode())
                        .productTypeId(d.getProductTypeId())
                        .source(d.getSource())
                        .quantity(d.getQuantity())
                        .unlimited(d.isUnlimited())
                        .usedQuantity(d.getUsedQuantity())
                        .remainingQuantity(d.remainingQuantity())
                        .startsAt(d.getStartsAt())
                        .expiresAt(d.getExpiresAt())
                        .build())
                .toList();
        return ResponseEntity.ok(details);
    }
}
