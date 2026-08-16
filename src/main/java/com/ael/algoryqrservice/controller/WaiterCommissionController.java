package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.TableBillDtos;
import com.ael.algoryqrservice.service.MenuWaiterCommissionQueryService;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/waiter/commissions")
@RequiredArgsConstructor
public class WaiterCommissionController {

    private final MenuWaiterCommissionQueryService menuWaiterCommissionQueryService;
    private final SecurityUtils securityUtils;

    @GetMapping("/today")
    public ResponseEntity<TableBillDtos.TodayCommissionSummary> getToday() {
        Long waiterId = securityUtils.getCurrentWaiterId();
        return ResponseEntity.ok(menuWaiterCommissionQueryService.getTodaySummary(waiterId));
    }

    @GetMapping("/history")
    public ResponseEntity<TableBillDtos.CommissionHistoryResponse> getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long waiterId = securityUtils.getCurrentWaiterId();
        return ResponseEntity.ok(menuWaiterCommissionQueryService.getHistory(waiterId, from, to, page, size));
    }
}
