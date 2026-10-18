package com.ael.algoryqrservice.billservice.application;

import com.ael.algoryqrservice.billservice.dto.TableBillDtos;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.TableBill;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.repository.TableBillRepository;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.RestaurantTable;
import com.ael.algoryqrservice.qrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.staffservice.application.StaffAccessService;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Staff-facing bill use cases; every call is scoped to the current staff member's branch. */
@Service
@RequiredArgsConstructor
public class StaffBillService {

    private final TableBillService tableBillService;
    private final TableBillRepository tableBillRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final StaffAccessService staffAccessService;

    @Transactional(readOnly = true)
    public Optional<TableBillDtos.BillResponse> findOpenBillForTable(Long tableId) {
        RestaurantTable table = requireTable(tableId, staffAccessService.requireCurrentStaff());
        return tableBillService.findOpenBillForTable(table.getMenuId(), tableId);
    }

    @Transactional(readOnly = true)
    public TableBillDtos.BillResponse getBill(Long billId) {
        TableBill bill = requireBill(billId, staffAccessService.requireCurrentStaff());
        return tableBillService.getBill(bill.getMenuId(), billId);
    }

    @Transactional
    public TableBillDtos.BillResponse addItems(Long billId, TableBillDtos.UpdateBillItemsRequest request) {
        MerchantStaff staff = staffAccessService.requireCurrentStaff();
        TableBill bill = requireBill(billId, staff);
        return tableBillService.addItems(bill.getMenuId(), billId, request.getItems(), staff.getId());
    }

    @Transactional
    public TableBillDtos.BillResponse updateItemQuantity(Long billId, Long itemId, int quantity) {
        TableBill bill = requireBill(billId, staffAccessService.requireCurrentStaff());
        return tableBillService.updateItemQuantity(bill.getMenuId(), billId, itemId, quantity);
    }

    @Transactional
    public TableBillDtos.BillResponse removeItem(Long billId, Long itemId) {
        TableBill bill = requireBill(billId, staffAccessService.requireCurrentStaff());
        return tableBillService.removeItem(bill.getMenuId(), billId, itemId);
    }

    @Transactional
    public TableBillDtos.BillResponse payItems(Long billId, TableBillDtos.PayBillItemsRequest request) {
        MerchantStaff staff = staffAccessService.requireCurrentStaff();
        TableBill bill = requireBill(billId, staff);
        return tableBillService.payItems(bill.getMenuId(), billId, staff, request);
    }

    @Transactional
    public TableBillDtos.BillResponse payShare(Long billId, TableBillDtos.PayBillShareRequest request) {
        MerchantStaff staff = staffAccessService.requireCurrentStaff();
        TableBill bill = requireBill(billId, staff);
        return tableBillService.payShare(bill.getMenuId(), billId, staff, request);
    }

    @Transactional(readOnly = true)
    public TableBillDtos.SplitPreviewResponse getSplitPreview(Long billId, int personCount) {
        TableBill bill = requireBill(billId, staffAccessService.requireCurrentStaff());
        return tableBillService.getSplitPreview(bill.getMenuId(), billId, personCount);
    }

    @Transactional
    public TableBillDtos.BillResponse closeBill(Long billId, TableBillDtos.CloseBillRequest request) {
        MerchantStaff staff = staffAccessService.requireCurrentStaff();
        TableBill bill = requireBill(billId, staff);
        return tableBillService.closeBill(
                bill.getMenuId(),
                billId,
                staff,
                request.getPaymentMethod(),
                request.getTipReceived(),
                request.getTipAmount()
        );
    }

    @Transactional
    public TableBillDtos.BillResponse cancelBill(Long billId, TableBillDtos.CancelBillRequest request) {
        MerchantStaff staff = staffAccessService.requireCurrentStaff();
        TableBill bill = requireBill(billId, staff);
        return tableBillService.cancelBill(bill.getMenuId(), billId, staff, request);
    }

    private RestaurantTable requireTable(Long tableId, MerchantStaff staff) {
        RestaurantTable table = restaurantTableRepository.findById(tableId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
        staffAccessService.requireMenuInWaiterBranch(table.getMenuId(), staff);
        return table;
    }

    private TableBill requireBill(Long billId, MerchantStaff staff) {
        TableBill bill = tableBillRepository.findWithItemsById(billId)
                .orElseThrow(() -> new NotFoundException("Adisyon bulunamadı"));
        staffAccessService.requireMenuInWaiterBranch(bill.getMenuId(), staff);
        return bill;
    }
}
