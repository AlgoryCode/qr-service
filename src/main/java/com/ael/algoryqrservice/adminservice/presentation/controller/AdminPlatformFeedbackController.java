package com.ael.algoryqrservice.adminservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.platform.dto.PlatformFeedbackDtos;
import com.ael.algoryqrservice.qrservice.model.enums.PlatformFeedbackStatus;
import com.ael.algoryqrservice.qrservice.platform.service.PlatformFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/platform-feedbacks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlatformFeedbackController {

    private final PlatformFeedbackService platformFeedbackService;
    private final com.ael.algoryqrservice.adminservice.application.SupportTicketService supportTicketService;
    private final com.ael.algoryqrservice.adminservice.application.DashboardSecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<PlatformFeedbackDtos.FeedbackPageResponse> list(
            @RequestParam(required = false) PlatformFeedbackStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(platformFeedbackService.listForAdmin(status, q, priority, assigneeId, page, size));
    }

    @GetMapping("/summary")
    public com.ael.algoryqrservice.adminservice.application.SupportTicketService.Summary summary() {
        return supportTicketService.summary();
    }

    @GetMapping("/{id}/messages")
    public java.util.List<com.ael.algoryqrservice.adminservice.application.SupportTicketService.MessageView> messages(
            @PathVariable Long id) {
        return supportTicketService.messages(id);
    }

    public record ReplyRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 8000) String body,
                               boolean internal) {
    }

    @PostMapping("/{id}/messages")
    public com.ael.algoryqrservice.adminservice.application.SupportTicketService.MessageView reply(
            @PathVariable Long id, @Valid @RequestBody ReplyRequest request) {
        var me = securityUtils.getCurrentDashboardUser();
        return supportTicketService.reply(id, request.body(), request.internal(),
                me.getUsername() != null ? me.getUsername() : me.getEmail());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlatformFeedbackDtos.FeedbackItemResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(platformFeedbackService.getByIdForAdmin(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PlatformFeedbackDtos.FeedbackItemResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PlatformFeedbackDtos.AdminUpdateRequest request
    ) {
        return ResponseEntity.ok(platformFeedbackService.updateForAdmin(id, request));
    }
}
