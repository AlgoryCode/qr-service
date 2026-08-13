package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.dto.SmartReportDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.AnalyticsService;
import com.ael.algoryqrservice.service.SmartReportService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SmartReportService smartReportService;
    private final SecurityUtils securityUtils;

    @PostMapping("/menu/{menuId}/events")
    public ResponseEntity<Void> recordEvents(
            @PathVariable Long menuId,
            @Valid @RequestBody AnalyticsDtos.AnalyticsEventsRequest body,
            HttpServletRequest request
    ) {
        String ip = analyticsService.extractIpAddress(request);
        String userAgent = analyticsService.extractUserAgent(request);
        analyticsService.recordEvents(menuId, body, ip, userAgent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/menu/{menuId}/report")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<AnalyticsDtos.MenuAnalyticsReportResponse> getMenuReport(
            @PathVariable Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(analyticsService.getMenuReport(menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/menu/{menuId}/revenue")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<AnalyticsDtos.MenuRevenueReportResponse> getMenuRevenueReport(
            @PathVariable Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(analyticsService.getMenuRevenueReport(menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @PostMapping("/menu/{menuId}/smart-reports")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportAccepted> createSmartReport(
            @PathVariable Long menuId,
            @Valid @RequestBody SmartReportDtos.SmartReportCreateRequest body
    ) {
        Long ownerId = securityUtils.getCurrentUser().getId();
        SmartReportDtos.SmartReportAccepted accepted = smartReportService.enqueue(
                menuId,
                ownerId,
                body.from(),
                body.to(),
                body.locale(),
                body.options()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }

    @GetMapping("/smart-reports/quota")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportQuotaResponse> getSmartReportQuota() {
        Long ownerId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(smartReportService.getQuota(ownerId));
    }

    @GetMapping("/smart-reports")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<Page<SmartReportDtos.SmartReportListItem>> listSmartReports(
            @RequestParam(required = false, defaultValue = "completed") String status,
            Pageable pageable
    ) {
        Long ownerId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(smartReportService.listJobs(ownerId, status, pageable));
    }

    @GetMapping("/smart-reports/{jobId}")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportDetailResponse> getSmartReport(
            @PathVariable UUID jobId
    ) {
        Long ownerId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(smartReportService.getJobDetail(ownerId, jobId));
    }

    @PostMapping("/menu/{menuId}/visit")
    public ResponseEntity<Void> recordMenuVisit(
            @PathVariable Long menuId,
            HttpServletRequest request
    ) {
        String ip = analyticsService.extractIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        analyticsService.recordMenuVisit(menuId, ip, userAgent);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/menu/{menuId}/product/{productId}/visit")
    public ResponseEntity<Void> recordProductVisit(
            @PathVariable Long menuId,
            @PathVariable Long productId,
            HttpServletRequest request
    ) {
        String ip = analyticsService.extractIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        analyticsService.recordProductVisit(menuId, productId, ip, userAgent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/menu/{menuId}")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<AnalyticsDtos.VisitPageResponse> getMenuAnalytics(
            @PathVariable Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(analyticsService.getMenuAnalytics(menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/menu/{menuId}/product/{productId}")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<AnalyticsDtos.VisitPageResponse> getProductAnalytics(
            @PathVariable Long menuId,
            @PathVariable Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(analyticsService.getProductAnalytics(menuId, productId, ownerId, effectiveFrom, effectiveTo));
    }
}
