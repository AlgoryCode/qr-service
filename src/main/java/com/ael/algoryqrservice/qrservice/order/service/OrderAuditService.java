package com.ael.algoryqrservice.qrservice.order.service;

import com.ael.algoryqrservice.qrservice.model.MenuOrder;
import com.ael.algoryqrservice.qrservice.model.OrderAuditLog;
import com.ael.algoryqrservice.qrservice.model.enums.OrderAuditAction;
import com.ael.algoryqrservice.qrservice.repository.OrderAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderAuditService {

    private final OrderAuditLogRepository orderAuditLogRepository;

    @Transactional
    public void record(MenuOrder order, OrderAuditAction action, Long staffId, String detailJson) {
        if (order == null || order.getId() == null) {
            return;
        }
        orderAuditLogRepository.save(OrderAuditLog.builder()
                .menuId(order.getMenuId())
                .orderId(order.getId())
                .billId(order.getBillId())
                .staffId(staffId)
                .action(action)
                .detailJson(detailJson)
                .build());
    }
}
