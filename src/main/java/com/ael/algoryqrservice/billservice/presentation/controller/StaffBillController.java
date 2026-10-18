package com.ael.algoryqrservice.billservice.presentation.controller;

import com.ael.algoryqrservice.billservice.application.BillAdjustmentService;
import com.ael.algoryqrservice.billservice.application.StaffBillService;
import com.ael.algoryqrservice.billservice.dto.TableBillDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/staff/bills/{billId}")
@RequiredArgsConstructor
public class StaffBillController {

    private final StaffBillService staffBillService;
    private final BillAdjustmentService billAdjustmentService;

    @GetMapping
    public ResponseEntity<TableBillDtos.BillResponse> getBill(@PathVariable Long billId) {
        return ResponseEntity.ok(staffBillService.getBill(billId));
    }

    @PostMapping("/items")
    public ResponseEntity<TableBillDtos.BillResponse> addItems(
            @PathVariable Long billId,
            @Valid @RequestBody TableBillDtos.UpdateBillItemsRequest request
    ) {
        return ResponseEntity.ok(staffBillService.addItems(billId, request));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<TableBillDtos.BillResponse> updateItemQuantity(
            @PathVariable Long billId,
            @PathVariable Long itemId,
            @Valid @RequestBody TableBillDtos.UpdateBillItemQuantityRequest request
    ) {
        return ResponseEntity.ok(staffBillService.updateItemQuantity(billId, itemId, request.getQuantity()));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<TableBillDtos.BillResponse> removeItem(
            @PathVariable Long billId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(staffBillService.removeItem(billId, itemId));
    }

    @PostMapping("/payments/items")
    public ResponseEntity<TableBillDtos.BillResponse> payItems(
            @PathVariable Long billId,
            @Valid @RequestBody TableBillDtos.PayBillItemsRequest request
    ) {
        return ResponseEntity.ok(staffBillService.payItems(billId, request));
    }

    @PostMapping("/payments/share")
    public ResponseEntity<TableBillDtos.BillResponse> payShare(
            @PathVariable Long billId,
            @Valid @RequestBody TableBillDtos.PayBillShareRequest request
    ) {
        return ResponseEntity.ok(staffBillService.payShare(billId, request));
    }

    @GetMapping("/split")
    public ResponseEntity<TableBillDtos.SplitPreviewResponse> getSplitPreview(
            @PathVariable Long billId,
            @RequestParam int personCount
    ) {
        return ResponseEntity.ok(staffBillService.getSplitPreview(billId, personCount));
    }

    @PostMapping("/close")
    public ResponseEntity<TableBillDtos.BillResponse> closeBill(
            @PathVariable Long billId,
            @Valid @RequestBody TableBillDtos.CloseBillRequest request
    ) {
        return ResponseEntity.ok(staffBillService.closeBill(billId, request));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<TableBillDtos.AdjustmentResponse> createAdjustment(
            @PathVariable Long billId,
            @Valid @RequestBody TableBillDtos.CreateAdjustmentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billAdjustmentService.create(billId, request));
    }
}
