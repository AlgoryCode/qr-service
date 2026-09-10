package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.BillAdjustment;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.WorkShiftDtos;
import com.ael.algoryqrservice.service.BillAdjustmentService;
import com.ael.algoryqrservice.service.WorkShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waiter")
@RequiredArgsConstructor
public class WaiterOpsController {

    private final WorkShiftService workShiftService;
    private final BillAdjustmentService billAdjustmentService;

    @GetMapping("/shifts/current")
    public ResponseEntity<WorkShiftDtos.ShiftResponse> currentShift() {
        return ResponseEntity.ok(workShiftService.currentOpen());
    }

    @PostMapping("/shifts/open")
    public ResponseEntity<WorkShiftDtos.ShiftResponse> openShift(
            @Valid @RequestBody WorkShiftDtos.OpenShiftRequest request
    ) {
        return ResponseEntity.ok(workShiftService.openShift(request));
    }

    @PostMapping("/shifts/{shiftId}/close")
    public ResponseEntity<WorkShiftDtos.ShiftResponse> closeShift(
            @PathVariable Long shiftId,
            @Valid @RequestBody WorkShiftDtos.CloseShiftRequest request
    ) {
        return ResponseEntity.ok(workShiftService.closeShift(shiftId, request));
    }

    @PostMapping("/bill-adjustments")
    public ResponseEntity<BillAdjustment> createAdjustment(
            @Valid @RequestBody MenuOrderDtos.BillAdjustmentRequest request
    ) {
        return ResponseEntity.ok(billAdjustmentService.create(request));
    }
}
