package com.ael.algoryqrservice.staffservice.presentation.controller;

import com.ael.algoryqrservice.staffservice.dto.WorkShiftDtos;
import com.ael.algoryqrservice.staffservice.application.WorkShiftService;
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
@RequestMapping({"/staff/ops", "/waiter"})
@RequiredArgsConstructor
public class StaffOpsController {

    private final WorkShiftService workShiftService;

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
}
