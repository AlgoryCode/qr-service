package com.ael.algoryqrservice.staffservice.presentation.controller;

import com.ael.algoryqrservice.qrservice.order.service.WaiterCourierOrderService;
import com.ael.algoryqrservice.qrservice.store.model.dto.StoreOrderDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/staff/courier", "/waiter/courier"})
@RequiredArgsConstructor
public class StaffCourierController {

    private final WaiterCourierOrderService waiterCourierOrderService;

    @GetMapping("/orders")
    public ResponseEntity<List<StoreOrderDtos.OrderDetail>> listQueue() {
        return ResponseEntity.ok(waiterCourierOrderService.listQueue());
    }

    @PostMapping("/orders/{orderId}/dispatch")
    public ResponseEntity<StoreOrderDtos.OrderDetail> dispatch(@PathVariable Long orderId) {
        return ResponseEntity.ok(waiterCourierOrderService.dispatch(orderId));
    }

    @PostMapping("/orders/{orderId}/deliver")
    public ResponseEntity<StoreOrderDtos.OrderDetail> deliver(@PathVariable Long orderId) {
        return ResponseEntity.ok(waiterCourierOrderService.deliver(orderId));
    }
}
