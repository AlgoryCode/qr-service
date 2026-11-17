package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.DashboardAccountService;
import com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils;
import com.ael.algoryqrservice.adminservice.application.DashboardSessionService;
import com.ael.algoryqrservice.adminservice.dto.DashboardAuthDtos;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin panel accounts. Super admin only (see AdminPermissions). */
@RestController
@RequestMapping("/api/v1/admin/dashboard-users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardUserController {

    private final DashboardAccountService accountService;
    private final DashboardSessionService sessionService;
    private final DashboardSecurityUtils securityUtils;

    @GetMapping
    public List<DashboardAuthDtos.DashboardUserResponse> list() {
        return accountService.list().stream().map(AdminDashboardUserController::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<DashboardAuthDtos.DashboardUserResponse> create(
            @Valid @RequestBody DashboardAuthDtos.CreateDashboardUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(accountService.create(request)));
    }

    @PatchMapping("/{id}")
    public DashboardAuthDtos.DashboardUserResponse update(
            @PathVariable Long id, @Valid @RequestBody DashboardAuthDtos.UpdateDashboardUserRequest request) {
        return toResponse(accountService.update(id, request, securityUtils.getCurrentDashboardUser().getId()));
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<Void> setPassword(@PathVariable Long id, @Valid @RequestBody DashboardAuthDtos.SetPasswordRequest request) {
        accountService.setPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mfa/reset")
    public ResponseEntity<Void> resetMfa(@PathVariable Long id) {
        accountService.resetMfa(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/sessions")
    public List<DashboardAuthDtos.SessionResponse> sessions(@PathVariable Long id) {
        return sessionService.sessionsOf(id).stream()
                .map(s -> DashboardAuthController.toResponse(s, null))
                .toList();
    }

    @PostMapping("/{id}/sessions/revoke-all")
    public java.util.Map<String, Integer> revokeAll(@PathVariable Long id) {
        return java.util.Map.of("revoked", sessionService.revokeAll(id));
    }

    static DashboardAuthDtos.DashboardUserResponse toResponse(DashboardUser u) {
        return new DashboardAuthDtos.DashboardUserResponse(u.getId(), u.getEmail(), u.getUsername(), u.getFirstName(),
                u.getLastName(), u.getRole(), u.isActive(), u.isTotpEnabled(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
