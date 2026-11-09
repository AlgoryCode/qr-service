package com.ael.algoryqrservice.qrservice.orderflow.controller;

import com.ael.algoryqrservice.qrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.qrservice.orderflow.dto.OrderFlowDtos;
import com.ael.algoryqrservice.qrservice.orderflow.service.OrderFlowService;
import com.ael.algoryqrservice.qrservice.security.RequiresProductScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** İşletme sahibinin sipariş akışlarını kurduğu, şubelere atadığı ve yeni adım talep ettiği uçlar. */
@RestController
@RequestMapping("/api/v1/order-flows")
@RequiredArgsConstructor
public class OrderFlowController {

    private final OrderFlowService orderFlowService;

    @GetMapping("/catalog")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<OrderFlowDtos.CatalogResponse> catalog() {
        return ResponseEntity.ok(orderFlowService.catalog());
    }

    @GetMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<List<OrderFlowDtos.FlowResponse>> list() {
        return ResponseEntity.ok(orderFlowService.list());
    }

    @PostMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<OrderFlowDtos.FlowResponse> create(@Valid @RequestBody OrderFlowDtos.SaveFlowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderFlowService.create(request));
    }

    @PutMapping("/{flowId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<OrderFlowDtos.FlowResponse> update(
            @PathVariable Long flowId,
            @Valid @RequestBody OrderFlowDtos.SaveFlowRequest request
    ) {
        return ResponseEntity.ok(orderFlowService.update(flowId, request));
    }

    @DeleteMapping("/{flowId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<Void> delete(@PathVariable Long flowId) {
        orderFlowService.delete(flowId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{flowId}/branches/{branchId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<OrderFlowDtos.BranchFlowResponse> assign(@PathVariable Long flowId, @PathVariable Long branchId) {
        return ResponseEntity.ok(orderFlowService.assign(flowId, branchId));
    }

    @GetMapping("/branches/{branchId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<OrderFlowDtos.BranchFlowResponse> branchFlow(@PathVariable Long branchId) {
        return ResponseEntity.ok(orderFlowService.branchFlow(orderFlowService.ownedBranchId(branchId)));
    }

    @DeleteMapping("/branches/{branchId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<OrderFlowDtos.BranchFlowResponse> unassign(@PathVariable Long branchId) {
        return ResponseEntity.ok(orderFlowService.unassign(branchId));
    }

    @GetMapping("/requests")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<List<OrderFlowDtos.RequestResponse>> listRequests() {
        return ResponseEntity.ok(orderFlowService.listRequests());
    }

    @PostMapping("/requests")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<OrderFlowDtos.RequestResponse> createRequest(
            @Valid @RequestBody OrderFlowDtos.CreateRequestRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderFlowService.createRequest(request));
    }
}
