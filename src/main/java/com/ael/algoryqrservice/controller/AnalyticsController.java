package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.AnalyticsService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
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
    @RequiresProductScope(CatalogScopes.QR_ANALYTICS_OWNER)
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
    @RequiresProductScope(CatalogScopes.QR_ANALYTICS_OWNER)
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
    @RequiresProductScope(CatalogScopes.QR_ANALYTICS_OWNER)
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
