package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.AiUsageService;
import com.ael.algoryqrservice.adminservice.application.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** System health and AI usage; super admins only (see AdminPermissions "system"). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {

    private final SystemHealthService healthService;
    private final AiUsageService aiUsageService;

    @GetMapping("/api/v1/admin/system/health")
    public SystemHealthService.Health health() {
        return healthService.check();
    }

    @GetMapping("/api/v1/admin/system/ai-usage")
    public AiUsageService.Report aiUsage(@RequestParam(defaultValue = "30") int days) {
        return aiUsageService.report(days);
    }
}
