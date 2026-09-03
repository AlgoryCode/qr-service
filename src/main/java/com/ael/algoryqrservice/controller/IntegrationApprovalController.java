package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.IntegrationPendingProductDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.IntegrationApprovalService;
import com.ael.algoryqrservice.service.IntegrationExportService;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
public class IntegrationApprovalController {

    private final IntegrationApprovalService approvalService;
    private final IntegrationExportService exportService;
    private final SecurityUtils securityUtils;

    @PostMapping("/menus/{menuId}/export-to-ubereats")
    public ResponseEntity<IntegrationPendingProductDtos.JobAccepted> exportToUberEats(@PathVariable Long menuId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(exportService.exportToUberEats(menuId));
    }

    @PostMapping("/menus/{menuId}/import-from-ubereats")
    public ResponseEntity<IntegrationPendingProductDtos.JobAccepted> importFromUberEats(@PathVariable Long menuId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(exportService.importFromUberEats(menuId));
    }

    @GetMapping("/pending-products/menus/{menuId}")
    public ResponseEntity<Page<IntegrationPendingProductDtos.Response>> list(
            @PathVariable Long menuId,
            @RequestParam(required = false, defaultValue = "WAITING_APPROVAL") String status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(approvalService.list(menuId, status, pageable));
    }

    @PatchMapping("/pending-products/menus/{menuId}/{id}")
    public ResponseEntity<IntegrationPendingProductDtos.Response> update(
            @PathVariable Long menuId,
            @PathVariable UUID id,
            @RequestBody IntegrationPendingProductDtos.UpdateRequest request
    ) {
        return ResponseEntity.ok(approvalService.update(menuId, id, request));
    }

    @PostMapping("/pending-products/menus/{menuId}/{id}/approve")
    public ResponseEntity<IntegrationPendingProductDtos.Response> approve(
            @PathVariable Long menuId,
            @PathVariable UUID id,
            @Valid @RequestBody IntegrationPendingProductDtos.ApprovalRequest request
    ) {
        return ResponseEntity.ok(
                approvalService.approve(menuId, id, request, securityUtils.getCurrentUserId())
        );
    }

    @PostMapping("/pending-products/menus/{menuId}/bulk-approve")
    public ResponseEntity<List<IntegrationPendingProductDtos.Response>> bulkApprove(
            @PathVariable Long menuId,
            @Valid @RequestBody IntegrationPendingProductDtos.BulkApproveRequest request
    ) {
        return ResponseEntity.ok(
                approvalService.bulkApprove(menuId, request, securityUtils.getCurrentUserId())
        );
    }

    @PostMapping("/pending-products/menus/{menuId}/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long menuId,
            @PathVariable UUID id,
            @Valid @RequestBody IntegrationPendingProductDtos.RejectRequest request
    ) {
        approvalService.reject(menuId, id, request);
        return ResponseEntity.noContent().build();
    }
}
