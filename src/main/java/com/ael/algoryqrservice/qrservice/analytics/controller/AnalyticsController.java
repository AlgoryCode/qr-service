package com.ael.algoryqrservice.qrservice.analytics.controller;

import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.security.ProductUsageGateway;
import com.ael.algoryqrservice.qrservice.analytics.dto.AnalyticsDtos;
import com.ael.algoryqrservice.qrservice.analytics.dto.SmartReportDtos;
import com.ael.algoryqrservice.qrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.qrservice.analytics.service.AnalyticsService;
import com.ael.algoryqrservice.qrservice.menu.service.MenuService;
import com.ael.algoryqrservice.qrservice.analytics.service.SmartReportFeedbackService;
import com.ael.algoryqrservice.qrservice.analytics.service.SmartReportService;
import com.ael.algoryqrservice.qrservice.analytics.service.UnifiedAnalyticsService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;
import com.ael.algoryqrservice.qrservice.model.User;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UnifiedAnalyticsService unifiedAnalyticsService;
    private final SmartReportService smartReportService;
    private final SmartReportFeedbackService smartReportFeedbackService;
    private final ProductUsageGateway productUsageGateway;
    private final SecurityUtils securityUtils;
    private final MenuService menuService;

    @PostMapping("/menu/{publicId}/events")
    public ResponseEntity<Void> recordEvents(
            @PathVariable String publicId,
            @Valid @RequestBody AnalyticsDtos.AnalyticsEventsRequest body,
            HttpServletRequest request
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        String ip = analyticsService.extractIpAddress(request);
        String userAgent = analyticsService.extractUserAgent(request);
        analyticsService.recordEvents(menuId, body, ip, userAgent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branch/{branchId}/summary")
    public ResponseEntity<AnalyticsDtos.SummaryAnalyticsResponse> getBranchSummary(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        requireOrderAnalyticsScope(ownerId);
        return ResponseEntity.ok(unifiedAnalyticsService.getBranchSummary(
                branchId, menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/branch/{branchId}/full")
    public ResponseEntity<AnalyticsDtos.FullAnalyticsResponse> getBranchFull(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        requireOrderAnalyticsScope(ownerId);
        return ResponseEntity.ok(unifiedAnalyticsService.getBranchFull(
                branchId, menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/branch/{branchId}/report")
    public ResponseEntity<AnalyticsDtos.MenuAnalyticsReportResponse> getBranchReport(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(analyticsService.getBranchReport(
                branchId, menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/branch/{branchId}/revenue")
    public ResponseEntity<AnalyticsDtos.MenuRevenueReportResponse> getBranchRevenueReport(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        requireOrderAnalyticsScope(ownerId);
        return ResponseEntity.ok(analyticsService.getBranchRevenueReport(
                branchId, menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/branch/{branchId}/waiter-performance")
    public ResponseEntity<AnalyticsDtos.MerchantStaffPerformanceReportResponse> getBranchWaiterPerformanceReport(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        requireOrderAnalyticsScope(ownerId);
        return ResponseEntity.ok(analyticsService.getBranchWaiterPerformanceReport(
                branchId, menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/menu/{menuId}/report")
    public ResponseEntity<AnalyticsDtos.MenuAnalyticsReportResponse> getMenuReport(
            @PathVariable Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(analyticsService.getMenuReport(menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/menu/{menuId}/revenue")
    public ResponseEntity<AnalyticsDtos.MenuRevenueReportResponse> getMenuRevenueReport(
            @PathVariable Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        requireOrderAnalyticsScope(ownerId);
        return ResponseEntity.ok(analyticsService.getMenuRevenueReport(menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/menu/{menuId}/waiter-performance")
    public ResponseEntity<AnalyticsDtos.MerchantStaffPerformanceReportResponse> getMerchantStaffPerformanceReport(
            @PathVariable Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        requireOrderAnalyticsScope(ownerId);
        return ResponseEntity.ok(
                analyticsService.getMerchantStaffPerformanceReport(menuId, ownerId, effectiveFrom, effectiveTo)
        );
    }

    @GetMapping("/merchant/detailed-report")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<AnalyticsDtos.MerchantDetailedReportResponse> getMerchantDetailedReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(29);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(analyticsService.getMerchantDetailedReport(ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/smart-reports/quota")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportQuotaResponse> getSmartReportQuota() {
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(smartReportService.getQuota(ownerId));
    }

    /** Reports created in the last 7 or 30 days, split into free, feedback-bonus and purchased credits. */
    @GetMapping("/smart-reports/usage")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportUsageResponse> getSmartReportUsage(
            @RequestParam(defaultValue = "7") int days
    ) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(smartReportService.getUsage(ownerId, days));
    }

    @GetMapping("/smart-reports")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<Page<SmartReportDtos.SmartReportListItem>> listSmartReports(
            @RequestParam(required = false, defaultValue = "completed") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable
    ) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(smartReportService.listJobs(ownerId, status, from, to, pageable));
    }

    @GetMapping("/smart-reports/{jobId}")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportDetailResponse> getSmartReport(
            @PathVariable UUID jobId
    ) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(smartReportService.getJobDetail(ownerId, jobId));
    }

    @GetMapping("/smart-reports/{jobId}/feedback")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.FeedbackResponse> getSmartReportFeedback(@PathVariable UUID jobId) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        return smartReportFeedbackService.find(ownerId, jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/smart-reports/{jobId}/feedback")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.FeedbackResponse> saveSmartReportFeedback(
            @PathVariable UUID jobId,
            @RequestBody SmartReportDtos.FeedbackRequest request
    ) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(smartReportFeedbackService.save(ownerId, jobId, request));
    }

    /** Saves the feedback and queues a revised report over the same scope and period (uses 1 credit). */
    @PostMapping("/smart-reports/{jobId}/revise")
    @RequiresProductScope(CatalogScopes.SMART_REPORTING_OWNER)
    public ResponseEntity<SmartReportDtos.SmartReportAccepted> reviseSmartReport(
            @PathVariable UUID jobId,
            @RequestBody SmartReportDtos.FeedbackRequest request
    ) {
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(smartReportFeedbackService.revise(ownerId, jobId, request));
    }

    @PostMapping("/menu/{publicId}/visit")
    public ResponseEntity<Void> recordMenuVisit(
            @PathVariable String publicId,
            HttpServletRequest request
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        String ip = analyticsService.extractIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        analyticsService.recordMenuVisit(menuId, ip, userAgent);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/menu/{publicId}/product/{productId}/visit")
    public ResponseEntity<Void> recordProductVisit(
            @PathVariable String publicId,
            @PathVariable Long productId,
            HttpServletRequest request
    ) {
        Long menuId = menuService.requirePublicMenuId(publicId);
        String ip = analyticsService.extractIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        analyticsService.recordProductVisit(menuId, productId, ip, userAgent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/menu/{menuId}")
    public ResponseEntity<AnalyticsDtos.VisitPageResponse> getMenuAnalytics(
            @PathVariable Long menuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(analyticsService.getMenuAnalytics(menuId, ownerId, effectiveFrom, effectiveTo));
    }

    @GetMapping("/menu/{menuId}/product/{productId}")
    public ResponseEntity<AnalyticsDtos.VisitPageResponse> getProductAnalytics(
            @PathVariable Long menuId,
            @PathVariable Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        Long ownerId = securityUtils.getCurrentMerchantId();
        return ResponseEntity.ok(analyticsService.getProductAnalytics(menuId, productId, ownerId, effectiveFrom, effectiveTo));
    }

    private void requireOrderAnalyticsScope(Long merchantId) {
        try {
            productUsageGateway.allow(merchantId, CatalogProducts.SMART_REPORTING);
        } catch (ForbiddenException reportingDenied) {
            productUsageGateway.allow(merchantId, CatalogProducts.WAITER_PANEL);
        }
    }
}
