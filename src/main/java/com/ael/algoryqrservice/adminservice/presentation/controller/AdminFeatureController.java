package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.billing.admin.FeatureCatalogService;
import com.ael.algoryqrservice.qrservice.billing.admin.PlanVersionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Feature catalog and package versions. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeatureController {

    public record MoveRequest(Long fromVersionId, @NotNull Long toVersionId) {
    }

    private final FeatureCatalogService featureCatalog;
    private final PlanVersionService planVersions;

    @GetMapping("/api/v1/admin/features")
    public List<FeatureCatalogService.View> features() {
        return featureCatalog.list();
    }

    @PutMapping("/api/v1/admin/features")
    public FeatureCatalogService.View saveFeature(@Valid @RequestBody FeatureCatalogService.Request request) {
        return featureCatalog.save(request);
    }

    @GetMapping("/api/v1/admin/packages/{packageId}/versions")
    public List<PlanVersionService.VersionSummary> versions(@PathVariable Long packageId) {
        return planVersions.list(packageId);
    }

    @PostMapping("/api/v1/admin/packages/{packageId}/versions/move")
    public Map<String, Integer> moveSubscribers(@PathVariable Long packageId, @Valid @RequestBody MoveRequest request) {
        return Map.of("moved", planVersions.moveSubscribers(packageId, request.fromVersionId(), request.toVersionId()));
    }
}
