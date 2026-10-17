package com.ael.algoryqrservice.staffservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.order.dto.TableBillDtos;
import com.ael.algoryqrservice.staffservice.application.MenuWaiterCommissionQueryService;
import com.ael.algoryqrservice.staffservice.application.StaffAccessService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping({"/staff/commissions", "/waiter/commissions"})
@RequiredArgsConstructor
public class StaffCommissionController {

    private final MenuWaiterCommissionQueryService menuWaiterCommissionQueryService;
    private final StaffAccessService waiterAccessService;
    private final SecurityUtils securityUtils;

    @GetMapping("/today")
    public ResponseEntity<TableBillDtos.TodayCommissionSummary> getToday() {
        waiterAccessService.requireWaiterStaff();
        Long staffId = securityUtils.getCurrentWaiterId();
        return ResponseEntity.ok(menuWaiterCommissionQueryService.getTodaySummary(staffId));
    }

    @GetMapping("/history")
    public ResponseEntity<TableBillDtos.CommissionHistoryResponse> getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        waiterAccessService.requireWaiterStaff();
        Long staffId = securityUtils.getCurrentWaiterId();
        return ResponseEntity.ok(menuWaiterCommissionQueryService.getHistory(staffId, from, to, page, size));
    }
}
