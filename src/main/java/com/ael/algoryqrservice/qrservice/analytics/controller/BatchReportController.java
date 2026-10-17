package com.ael.algoryqrservice.qrservice.analytics.controller;

import com.ael.algoryqrservice.qrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.qrservice.analytics.dto.BatchReportDtos;
import com.ael.algoryqrservice.qrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.qrservice.analytics.service.BatchReportService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class BatchReportController {

    private final BatchReportService batchReportService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<Page<BatchReportDtos.BatchReportListItem>> listReports(Pageable pageable) {
        Long merchantId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(batchReportService.listReports(merchantId, pageable));
    }

    @GetMapping("/{batchId}")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<BatchReportDtos.BatchReportDetail> getReport(@PathVariable UUID batchId) {
        Long merchantId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(batchReportService.getReport(merchantId, batchId));
    }
}
