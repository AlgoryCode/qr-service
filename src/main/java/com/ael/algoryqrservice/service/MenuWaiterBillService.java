package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.dto.TableBillDtos;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuWaiterBillService {

    private final TableBillService tableBillService;
    private final TableBillRepository tableBillRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final WaiterAccessService waiterAccessService;

    @Transactional(readOnly = true)
    public TableBillDtos.BillResponse getOpenBillForTable(Long tableId) {
        RestaurantTable table = requireTableForCurrentWaiter(tableId);
        return tableBillService.getOpenBillForTable(table.getMenuId(), tableId);
    }

    @Transactional(readOnly = true)
    public TableBillDtos.BillResponse getBill(Long billId) {
        TableBill bill = requireBillForCurrentWaiter(billId);
        return tableBillService.getBill(bill.getMenuId(), billId);
    }

    @Transactional
    public TableBillDtos.BillResponse addItems(Long billId, TableBillDtos.UpdateBillItemsRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        TableBill bill = requireBillForWaiter(billId, waiter);
        return tableBillService.addItems(
                bill.getMenuId(),
                billId,
                request.getItems(),
                waiter.getId()
        );
    }

    @Transactional
    public TableBillDtos.BillResponse updateItemQuantity(Long billId, Long itemId, int quantity) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        TableBill bill = requireBillForWaiter(billId, waiter);
        return tableBillService.updateItemQuantity(bill.getMenuId(), billId, itemId, quantity);
    }

    @Transactional
    public TableBillDtos.BillResponse removeItem(Long billId, Long itemId) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        TableBill bill = requireBillForWaiter(billId, waiter);
        return tableBillService.removeItem(bill.getMenuId(), billId, itemId);
    }

    @Transactional
    public TableBillDtos.BillResponse payItems(Long billId, TableBillDtos.PayBillItemsRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        TableBill bill = requireBillForWaiter(billId, waiter);
        return tableBillService.payItems(bill.getMenuId(), billId, waiter, request);
    }

    @Transactional
    public TableBillDtos.BillResponse payShare(Long billId, TableBillDtos.PayBillShareRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        TableBill bill = requireBillForWaiter(billId, waiter);
        return tableBillService.payShare(bill.getMenuId(), billId, waiter, request);
    }

    @Transactional(readOnly = true)
    public TableBillDtos.SplitPreviewResponse getSplitPreview(Long billId, int personCount) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        TableBill bill = requireBillForWaiter(billId, waiter);
        return tableBillService.getSplitPreview(bill.getMenuId(), billId, personCount);
    }

    @Transactional
    public TableBillDtos.BillResponse closeBill(Long billId, TableBillDtos.CloseBillRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        TableBill bill = requireBillForWaiter(billId, waiter);
        return tableBillService.closeBill(
                bill.getMenuId(),
                billId,
                waiter,
                request != null ? request.getPaymentMethod() : null,
                request != null ? request.getTipReceived() : null,
                request != null ? request.getTipAmount() : null
        );
    }

    private RestaurantTable requireTableForCurrentWaiter(Long tableId) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        RestaurantTable table = restaurantTableRepository.findById(tableId)
                .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
        waiterAccessService.requireMenuInWaiterBranch(table.getMenuId(), waiter);
        return table;
    }

    private TableBill requireBillForCurrentWaiter(Long billId) {
        return requireBillForWaiter(billId, waiterAccessService.requireCurrentWaiter());
    }

    private TableBill requireBillForWaiter(Long billId, MenuWaiter waiter) {
        TableBill bill = tableBillRepository.findWithItemsById(billId)
                .orElseThrow(() -> new NotFoundException("Adisyon bulunamadı"));
        waiterAccessService.requireMenuInWaiterBranch(bill.getMenuId(), waiter);
        return bill;
    }
}
