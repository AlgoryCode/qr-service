package com.ael.algoryqrservice.qrservice.analytics.assistant;

import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AssistantReportController {

    private final AssistantReportService assistantReportService;
    private final SecurityUtils securityUtils;

    @PostMapping("/assistant-usage")
    public ResponseEntity<Void> recordUsage(@Valid @RequestBody AssistantReportDtos.RecordUsageRequest body) {
        assistantReportService.recordUsage(merchantId(), body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assistant-usage/today")
    public ResponseEntity<AssistantReportDtos.TodayUsageResponse> todayUsage() {
        return ResponseEntity.ok(assistantReportService.todayUsage(merchantId()));
    }

    @GetMapping("/branch/{branchId}/assistant")
    public ResponseEntity<AssistantReportDtos.AssistantReportResponse> branchReport(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(29);
        return ResponseEntity.ok(assistantReportService.branchReport(
                branchId, merchantId(), effectiveFrom, effectiveTo));
    }

    private Long merchantId() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        if (merchantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Merchant bulunamadı");
        }
        return merchantId;
    }
}
