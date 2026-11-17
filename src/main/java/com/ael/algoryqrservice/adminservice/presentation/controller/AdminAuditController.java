package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.adminservice.application.AdminAuditService;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.AdminAuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Who changed what in the admin panel. Super admin only (see AdminPermissions). */
@RestController
@RequestMapping("/api/v1/admin/audit-log")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AdminAuditService auditService;

    public record AuditEntry(Long id, Long actorId, String actorName, String actorRole, String method, String path,
                             String action, String targetType, String targetId, int status, String outcome,
                             String ipAddress, String userAgent, String requestBody, LocalDateTime createdAt) {
    }

    public record AuditPage(List<AuditEntry> items, long total, int page, int size, boolean hasNext) {
    }

    @GetMapping
    public AuditPage search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<AdminAuditLog> result = auditService.search(q, actorId, outcome, targetType, targetId,
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(),
                Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return new AuditPage(result.getContent().stream().map(AdminAuditController::toEntry).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize(), result.hasNext());
    }

    static AuditEntry toEntry(AdminAuditLog a) {
        return new AuditEntry(a.getId(), a.getActorId(), a.getActorName(), a.getActorRole(), a.getMethod(), a.getPath(),
                a.getAction(), a.getTargetType(), a.getTargetId(), a.getStatus(), a.getOutcome(), a.getIpAddress(),
                a.getUserAgent(), a.getRequestBody(), a.getCreatedAt());
    }
}
