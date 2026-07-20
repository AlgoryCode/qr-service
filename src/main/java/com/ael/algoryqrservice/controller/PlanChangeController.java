package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.PlanChangePreviewResponse;
import com.ael.algoryqrservice.model.dto.PlanChangeRequest;
import com.ael.algoryqrservice.model.dto.PlanChangeResponse;
import com.ael.algoryqrservice.service.PlanChangeService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/plan-changes")
@RequiredArgsConstructor
public class PlanChangeController {

    private final PlanChangeService planChangeService;
    private final SecurityUtils securityUtils;

    @GetMapping("/preview")
    public ResponseEntity<PlanChangePreviewResponse> preview(@RequestParam Long toPackageId) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(planChangeService.preview(userId, toPackageId));
    }

    @PostMapping
    public ResponseEntity<PlanChangeResponse> request(
            @Valid @RequestBody PlanChangeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = resolveClientIp(httpServletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                planChangeService.request(securityUtils.getCurrentUser(), request, clientIp)
        );
    }

    @GetMapping("/my")
    public ResponseEntity<List<PlanChangeResponse>> listMine() {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(planChangeService.listMine(userId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PlanChangeResponse> cancel(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(planChangeService.cancelScheduled(userId, id));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
