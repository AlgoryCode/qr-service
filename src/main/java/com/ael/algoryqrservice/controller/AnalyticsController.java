package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.enums.ProductScope;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.AnalyticsService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SecurityUtils securityUtils;

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
    @RequiresProductScope(ProductScope.QR_ANALYTICS_OWNER)
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
    @RequiresProductScope(ProductScope.QR_ANALYTICS_OWNER)
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
