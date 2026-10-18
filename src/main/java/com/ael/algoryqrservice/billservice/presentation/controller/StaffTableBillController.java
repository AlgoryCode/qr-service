package com.ael.algoryqrservice.billservice.presentation.controller;

import com.ael.algoryqrservice.billservice.application.StaffBillService;
import com.ael.algoryqrservice.billservice.dto.TableBillDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/staff/tables/{tableId}/bill")
@RequiredArgsConstructor
public class StaffTableBillController {

    private final StaffBillService staffBillService;

    /** 204 when the table has no open bill. */
    @GetMapping
    public ResponseEntity<TableBillDtos.BillResponse> getTableBill(@PathVariable Long tableId) {
        return staffBillService.findOpenBillForTable(tableId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
