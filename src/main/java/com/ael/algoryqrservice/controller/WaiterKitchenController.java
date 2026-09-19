package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.service.MenuWaiterOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/waiter/kitchen")
@RequiredArgsConstructor
public class WaiterKitchenController {

    private final MenuWaiterOrderService menuWaiterOrderService;

    @GetMapping("/orders")
    public ResponseEntity<List<MenuOrderDtos.OrderResponse>> listQueue() {
        return ResponseEntity.ok(menuWaiterOrderService.listKitchenQueue());
    }

    @PostMapping("/orders/{orderId}/preparing")
    public ResponseEntity<MenuOrderDtos.OrderResponse> markPreparing(
            @PathVariable Long orderId,
            @RequestParam(required = false) String source
    ) {
        return ResponseEntity.ok(menuWaiterOrderService.markPreparing(orderId, source));
    }

    @PostMapping("/orders/{orderId}/ready")
    public ResponseEntity<MenuOrderDtos.OrderResponse> markReady(
            @PathVariable Long orderId,
            @RequestParam(required = false) String source
    ) {
        return ResponseEntity.ok(menuWaiterOrderService.markReady(orderId, source));
    }

    @PatchMapping("/orders/{orderId}/note")
    public ResponseEntity<MenuOrderDtos.OrderResponse> updateNote(
            @PathVariable Long orderId,
            @Valid @RequestBody MenuWaiterDtos.WaiterNoteRequest request
    ) {
        String note = request != null ? request.getNote() : null;
        return ResponseEntity.ok(menuWaiterOrderService.updateKitchenNote(orderId, note));
    }
}
