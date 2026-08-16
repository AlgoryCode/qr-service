package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.PlatformFeedbackDtos;
import com.ael.algoryqrservice.model.enums.PlatformFeedbackStatus;
import com.ael.algoryqrservice.service.PlatformFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/platform-feedback")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlatformFeedbackController {

    private final PlatformFeedbackService platformFeedbackService;

    @GetMapping
    public ResponseEntity<PlatformFeedbackDtos.FeedbackPageResponse> list(
            @RequestParam(required = false) PlatformFeedbackStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(platformFeedbackService.listForAdmin(status, q, page, size));
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
