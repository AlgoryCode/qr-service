package com.ael.algoryqrservice.staffservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.orderflow.dto.OrderFlowDtos;
import com.ael.algoryqrservice.qrservice.orderflow.service.OrderFlowService;
import com.ael.algoryqrservice.staffservice.application.StaffAccessService;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Personel uygulamasının sekme ve düğmelerini belirleyen, kendi şubesinin geçerli sipariş akışı. */
@RestController
@RequestMapping("/api/v1/staff/order-flow")
@RequiredArgsConstructor
public class StaffOrderFlowController {

    private final OrderFlowService orderFlowService;
    private final StaffAccessService waiterAccessService;

    @GetMapping
    public ResponseEntity<OrderFlowDtos.BranchFlowResponse> current() {
        MerchantStaff staff = waiterAccessService.requireCurrentStaff();
        if (staff.getBranchId() == null) {
            throw new BadRequestException("Hesabınız bir şubeye atanmamış");
        }
        return ResponseEntity.ok(orderFlowService.branchFlow(staff.getBranchId()));
    }
}
