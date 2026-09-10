package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.BillAdjustment;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.enums.OrderAuditAction;
import com.ael.algoryqrservice.repository.BillAdjustmentRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BillAdjustmentService {

    private final BillAdjustmentRepository billAdjustmentRepository;
    private final TableBillRepository tableBillRepository;
    private final MenuOrderRepository menuOrderRepository;
    private final WaiterAccessService waiterAccessService;
    private final OrderAuditService orderAuditService;

    @Transactional
    public BillAdjustment create(MenuOrderDtos.BillAdjustmentRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Tutar geçersiz");
        }
        TableBill bill = tableBillRepository.findById(request.getBillId())
                .orElseThrow(() -> new NotFoundException("Adisyon bulunamadı"));
        waiterAccessService.requireMenuInWaiterBranch(bill.getMenuId(), waiter);

        BillAdjustment adjustment = BillAdjustment.builder()
                .menuId(bill.getMenuId())
                .billId(bill.getId())
                .orderId(request.getOrderId())
                .waiterId(waiter.getId())
                .adjustmentType(request.getAdjustmentType())
                .amount(request.getAmount())
                .reason(request.getReason())
                .reasonNote(request.getReasonNote())
                .build();
        BillAdjustment saved = billAdjustmentRepository.save(adjustment);

        if (request.getOrderId() != null) {
            MenuOrder order = menuOrderRepository.findById(request.getOrderId()).orElse(null);
            if (order != null) {
                orderAuditService.record(order, OrderAuditAction.ADJUSTMENT, waiter.getId(),
                        "{\"type\":\"" + request.getAdjustmentType() + "\",\"amount\":" + request.getAmount() + "}");
            }
        }
        return saved;
    }
}
