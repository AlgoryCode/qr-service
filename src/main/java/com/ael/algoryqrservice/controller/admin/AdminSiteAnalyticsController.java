package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.SiteAnalyticsDtos;
import com.ael.algoryqrservice.service.SiteAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/analytics/site-visits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSiteAnalyticsController {

    private final SiteAnalyticsService siteAnalyticsService;

    @GetMapping
    public ResponseEntity<SiteAnalyticsDtos.VisitPageResponse> listVisits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(siteAnalyticsService.listVisits(page, size, days));
    }

    @GetMapping("/summary")
    public ResponseEntity<SiteAnalyticsDtos.SummaryResponse> summary(
            @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(siteAnalyticsService.summary(days));
    }
}
