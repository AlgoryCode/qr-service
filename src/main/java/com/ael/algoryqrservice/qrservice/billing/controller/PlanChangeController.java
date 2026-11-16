package com.ael.algoryqrservice.qrservice.billing.controller;

import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangePreviewResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangeRequest;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanChangeResponse;
import com.ael.algoryqrservice.qrservice.billing.service.PlanChangeService;
import com.ael.algoryqrservice.qrservice.util.RequestUtils;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
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
@RequestMapping("/api/v1/plan-changes")
@RequiredArgsConstructor
public class PlanChangeController {

    private final PlanChangeService planChangeService;
    private final SecurityUtils securityUtils;

    @GetMapping("/preview")
    public ResponseEntity<PlanChangePreviewResponse> preview(@RequestParam Long toPackageId) {
        return ResponseEntity.ok(planChangeService.preview(securityUtils.getCurrentMerchantId(), toPackageId));
    }

    @PostMapping
    public ResponseEntity<PlanChangeResponse> request(
            @Valid @RequestBody PlanChangeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planChangeService.request(
                securityUtils.getCurrentUser(),
                request,
                RequestUtils.resolveClientIp(httpServletRequest)
        ));
    }

    @GetMapping("/my")
    public ResponseEntity<List<PlanChangeResponse>> listMine() {
        return ResponseEntity.ok(planChangeService.listMine(securityUtils.getCurrentMerchantId()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PlanChangeResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(planChangeService.cancel(securityUtils.getCurrentMerchantId(), id));
    }
}
