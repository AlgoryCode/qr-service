package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.AdminSubscriptionService;
import com.ael.algoryqrservice.adminservice.dto.AdminPackageDtos;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * A merchant's trial and package, as the admin panel changes them. The panel sends PATCH with a
 * status/days body; POST and DELETE are the same actions in resource form.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserPackageController {

    private final AdminSubscriptionService adminSubscriptionService;

    @PatchMapping("/{id}/trial")
    public ResponseEntity<?> updateTrial(
            @PathVariable Long id,
            @Valid @RequestBody AdminPackageDtos.TrialUpdateRequest request
    ) {
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            if (!"ENDED".equals(request.getStatus().trim().toUpperCase(Locale.ROOT))) {
                throw new BadRequestException("status yalnızca ENDED olabilir");
            }
            return ResponseEntity.ok(adminSubscriptionService.endTrial(id));
        }
        if (request.getDays() == null) {
            throw new BadRequestException("days veya status=ENDED gerekli");
        }
        return ResponseEntity.ok(adminSubscriptionService.extendTrial(id, request.getDays()));
    }

    @PostMapping("/{id}/trial")
    public ResponseEntity<AdminPackageDtos.ExtendTrialResponse> extendTrial(
            @PathVariable Long id,
            @Valid @RequestBody AdminPackageDtos.ExtendTrialRequest request
    ) {
        return ResponseEntity.ok(adminSubscriptionService.extendTrial(id, request.getDays()));
    }

    @DeleteMapping("/{id}/trial")
    public ResponseEntity<AdminPackageDtos.EndTrialResponse> endTrial(@PathVariable Long id) {
        return ResponseEntity.ok(adminSubscriptionService.endTrial(id));
    }

    @PatchMapping("/{id}/package")
    public ResponseEntity<AdminPackageDtos.PackageLifecycleResponse> updatePackage(
            @PathVariable Long id,
            @Valid @RequestBody AdminPackageDtos.PackageUpdateRequest request
    ) {
        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "INACTIVE" -> ResponseEntity.ok(adminSubscriptionService.deactivatePackage(id));
            case "ACTIVE", "" -> {
                if (request.getDays() == null) {
                    throw new BadRequestException("days gerekli");
                }
                yield ResponseEntity.ok(adminSubscriptionService.grantPackage(id, request.getDays()));
            }
            default -> throw new BadRequestException("status ACTIVE veya INACTIVE olmalı");
        };
    }

    @PostMapping("/{id}/package")
    public ResponseEntity<AdminPackageDtos.PackageLifecycleResponse> grantPackage(
            @PathVariable Long id,
            @Valid @RequestBody AdminPackageDtos.ReactivatePackageRequest request
    ) {
        return ResponseEntity.ok(adminSubscriptionService.grantPackage(id, request.getDays()));
    }

    @DeleteMapping("/{id}/package")
    public ResponseEntity<AdminPackageDtos.PackageLifecycleResponse> deactivatePackage(@PathVariable Long id) {
        return ResponseEntity.ok(adminSubscriptionService.deactivatePackage(id));
    }
}
