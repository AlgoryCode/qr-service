package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.CatalogScheduleService;
import com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils;
import com.ael.algoryqrservice.adminservice.application.ModuleShowcaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Scheduled price changes, discount campaigns and the module store showcase. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogPlanController {

    private final CatalogScheduleService scheduleService;
    private final ModuleShowcaseService showcaseService;
    private final DashboardSecurityUtils securityUtils;

    @GetMapping("/api/v1/admin/price-changes")
    public List<CatalogScheduleService.View> list() {
        return scheduleService.list();
    }

    @PostMapping("/api/v1/admin/price-changes")
    public CatalogScheduleService.View create(@Valid @RequestBody CatalogScheduleService.Request request) {
        return scheduleService.create(request, actor());
    }

    @PostMapping("/api/v1/admin/price-changes/{id}/cancel")
    public CatalogScheduleService.View cancel(@PathVariable Long id) {
        return scheduleService.cancel(id);
    }

    @GetMapping("/api/v1/admin/showcase")
    public List<ModuleShowcaseService.View> showcase() {
        return showcaseService.list();
    }

    @PutMapping("/api/v1/admin/showcase/{productId}")
    public ModuleShowcaseService.View saveShowcase(@PathVariable Long productId,
                                                   @Valid @RequestBody ModuleShowcaseService.Request request) {
        return showcaseService.save(productId, request, actor());
    }

    private String actor() {
        var me = securityUtils.getCurrentDashboardUser();
        return me.getUsername() != null ? me.getUsername() : me.getEmail();
    }
}
