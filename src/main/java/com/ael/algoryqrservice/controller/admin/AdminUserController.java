package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.service.AdminUserCredentialService;
import com.ael.algoryqrservice.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminUserCredentialService adminUserCredentialService;

    @GetMapping
    public ResponseEntity<AdminUserDtos.UserPageResponse> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(adminUserService.listUsers(q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDtos.UserDetailResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserDtos.UserDetailResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserDtos.UserUpdateRequest request
    ) {
        return ResponseEntity.ok(adminUserService.updateUser(id, request));
    }

    @PostMapping("/{id}/email-verifications")
    public ResponseEntity<Void> sendEmailVerification(@PathVariable Long id) {
        adminUserCredentialService.sendEmailVerification(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/password-resets")
    public ResponseEntity<AdminUserDtos.PasswordResetResponse> resetPassword(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserCredentialService.resetPassword(id));
    }
}
