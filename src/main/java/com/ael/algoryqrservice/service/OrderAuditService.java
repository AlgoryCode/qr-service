package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.OrderAuditLog;
import com.ael.algoryqrservice.model.enums.OrderAuditAction;
import com.ael.algoryqrservice.repository.OrderAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderAuditService {

    private final OrderAuditLogRepository orderAuditLogRepository;

    @Transactional
    public void record(MenuOrder order, OrderAuditAction action, Long waiterId, String detailJson) {
        if (order == null || order.getId() == null) {
            return;
        }
        orderAuditLogRepository.save(OrderAuditLog.builder()
                .menuId(order.getMenuId())
                .orderId(order.getId())
                .billId(order.getBillId())
                .waiterId(waiterId)
                .action(action)
                .detailJson(detailJson)
                .build());
    }
}
