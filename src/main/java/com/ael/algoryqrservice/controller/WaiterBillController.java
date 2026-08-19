package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.TableBillDtos;
import com.ael.algoryqrservice.service.MenuWaiterBillService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waiter/bills")
@RequiredArgsConstructor
public class WaiterBillController {

    private final MenuWaiterBillService menuWaiterBillService;
    private final SecurityUtils securityUtils;

    @GetMapping("/tables/{tableId}/open")
    public ResponseEntity<TableBillDtos.BillResponse> getOpenBillForTable(@PathVariable Long tableId) {
        Long menuId = securityUtils.getCurrentWaiterMenuId();
        return ResponseEntity.ok(menuWaiterBillService.getOpenBillForTable(menuId, tableId));
    }

    @GetMapping("/{billId}")
    public ResponseEntity<TableBillDtos.BillResponse> getBill(@PathVariable Long billId) {
        Long menuId = securityUtils.getCurrentWaiterMenuId();
        return ResponseEntity.ok(menuWaiterBillService.getBill(menuId, billId));
    }

    @PutMapping("/{billId}/items")
    public ResponseEntity<TableBillDtos.BillResponse> addItems(
            @PathVariable Long billId,
            @Valid @RequestBody TableBillDtos.UpdateBillItemsRequest request
    ) {
        return ResponseEntity.ok(menuWaiterBillService.addItems(billId, request));
    }

    @PutMapping("/{billId}/items/{itemId}")
    public ResponseEntity<TableBillDtos.BillResponse> updateItemQuantity(
            @PathVariable Long billId,
            @PathVariable Long itemId,
            @Valid @RequestBody TableBillDtos.UpdateBillItemQuantityRequest request
    ) {
        return ResponseEntity.ok(menuWaiterBillService.updateItemQuantity(
                billId,
                itemId,
                request.getQuantity()
        ));
    }

    @DeleteMapping("/{billId}/items/{itemId}")
    public ResponseEntity<TableBillDtos.BillResponse> removeItem(
            @PathVariable Long billId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(menuWaiterBillService.removeItem(billId, itemId));
    }

    @PostMapping("/{billId}/pay-items")
    public ResponseEntity<TableBillDtos.BillResponse> payItems(
            @PathVariable Long billId,
            @Valid @RequestBody TableBillDtos.PayBillItemsRequest request
    ) {
        return ResponseEntity.ok(menuWaiterBillService.payItems(billId, request));
    }

    @PostMapping("/{billId}/close")
    public ResponseEntity<TableBillDtos.BillResponse> closeBill(
            @PathVariable Long billId,
            @Valid @RequestBody TableBillDtos.CloseBillRequest request
    ) {
        return ResponseEntity.ok(menuWaiterBillService.closeBill(billId, request));
    }
}
