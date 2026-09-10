package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.FulfillmentUsageLog;
import com.ael.algoryqrservice.model.dto.FulfillmentDetailResponse;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.FulfillmentUsageLogRepository;
import com.ael.algoryqrservice.service.FulfillmentMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserFulfillmentController {

    private final FulfillmentMigrationService fulfillmentMigrationService;
    private final FulfillmentUsageLogRepository fulfillmentUsageLogRepository;
    private final FulfillmentDetailRepository fulfillmentDetailRepository;

    @PostMapping("/{id}/fulfillment-backfills")
    public ResponseEntity<FulfillmentMigrationService.MigrationResult> backfillUser(@PathVariable Long id) {
        return ResponseEntity.ok(fulfillmentMigrationService.backfillUser(id));
    }

    @GetMapping("/{id}/fulfillment-parity")
    public ResponseEntity<FulfillmentMigrationService.ParityReport> parityReport(@PathVariable Long id) {
        return ResponseEntity.ok(fulfillmentMigrationService.parityReport(id));
    }

    @GetMapping("/{id}/fulfillment-usage-logs")
    public ResponseEntity<Page<FulfillmentUsageLog>> usageLog(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(
                fulfillmentUsageLogRepository.findByUserIdOrderByCreatedAtDesc(id, PageRequest.of(page, size))
        );
    }

    @GetMapping("/{id}/fulfillment-details")
    public ResponseEntity<List<FulfillmentDetailResponse>> fulfillmentDetails(@PathVariable Long id) {
        LocalDateTime now = LocalDateTime.now();
        List<FulfillmentDetailResponse> details = fulfillmentDetailRepository
                .findAllActiveByUserId(id, now).stream()
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
