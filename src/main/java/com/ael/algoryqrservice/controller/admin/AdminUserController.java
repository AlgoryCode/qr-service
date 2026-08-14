package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.service.AdminUserService;
import com.ael.algoryqrservice.util.ClientInfo;
import com.ael.algoryqrservice.util.DashboardSecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final DashboardSecurityUtils dashboardSecurityUtils;

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

    @PostMapping("/{id}/impersonate")
    public ResponseEntity<AdminUserDtos.ImpersonateResponse> impersonateUser(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(adminUserService.impersonateUser(
                id,
                dashboardSecurityUtils.getCurrentDashboardUser(),
                ClientInfo.from(httpRequest)
        ));
    }
}
