package com.ael.algoryqrservice.billservice.application;

import com.ael.algoryqrservice.billservice.dto.TableBillDtos;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.BillAdjustment;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.TableBill;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.repository.BillAdjustmentRepository;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.repository.TableBillRepository;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.enums.OrderAuditAction;
import com.ael.algoryqrservice.qrservice.order.service.OrderAuditService;
import com.ael.algoryqrservice.qrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.staffservice.application.StaffAccessService;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillAdjustmentService {

    private final BillAdjustmentRepository billAdjustmentRepository;
    private final TableBillRepository tableBillRepository;
    private final MenuOrderRepository menuOrderRepository;
    private final StaffAccessService staffAccessService;
    private final OrderAuditService orderAuditService;

    @Transactional
    public TableBillDtos.AdjustmentResponse create(Long billId, TableBillDtos.CreateAdjustmentRequest request) {
        MerchantStaff staff = staffAccessService.requireCurrentStaff();
        TableBill bill = tableBillRepository.findById(billId)
                .orElseThrow(() -> new NotFoundException("Adisyon bulunamadı"));
        staffAccessService.requireMenuInWaiterBranch(bill.getMenuId(), staff);

        BillAdjustment adjustment = billAdjustmentRepository.save(BillAdjustment.builder()
                .menuId(bill.getMenuId())
                .billId(bill.getId())
                .orderId(request.getOrderId())
                .staffId(staff.getId())
                .adjustmentType(request.getAdjustmentType())
                .amount(request.getAmount())
                .reason(request.getReason())
                .reasonNote(request.getReasonNote())
                .build());

        recordOrderAudit(adjustment, staff);
        return toResponse(adjustment);
    }

    private void recordOrderAudit(BillAdjustment adjustment, MerchantStaff staff) {
        if (adjustment.getOrderId() == null) {
            return;
        }
        menuOrderRepository.findById(adjustment.getOrderId()).ifPresent(order -> orderAuditService.record(
                order,
                OrderAuditAction.ADJUSTMENT,
                staff.getId(),
                "{\"type\":\"" + adjustment.getAdjustmentType() + "\",\"amount\":" + adjustment.getAmount() + "}"
        ));
    }

    private static TableBillDtos.AdjustmentResponse toResponse(BillAdjustment adjustment) {
        return TableBillDtos.AdjustmentResponse.builder()
                .id(adjustment.getId())
                .billId(adjustment.getBillId())
                .orderId(adjustment.getOrderId())
                .staffId(adjustment.getStaffId())
                .adjustmentType(adjustment.getAdjustmentType())
                .amount(adjustment.getAmount())
                .reason(adjustment.getReason())
                .reasonNote(adjustment.getReasonNote())
                .createdAt(adjustment.getCreatedAt())
                .build();
    }
}
