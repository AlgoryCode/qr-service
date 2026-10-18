package com.ael.algoryqrservice.merchantservice.presentation.controller;

import com.ael.algoryqrservice.fulfillmentservice.application.service.EntitlementUsageService;
import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.ResourceNotFoundException;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.request.AssignPackageRequest;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.request.EntitlementQuantityRequest;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ActivePackageResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ConsumeEntitlementResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.EntitlementResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.FulfillmentResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ProductAccessResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.mapper.FulfillmentPresentationMapper;
import com.ael.algoryqrservice.qrservice.account.dto.SessionContextResponse;
import com.ael.algoryqrservice.qrservice.account.service.SessionContextService;
import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.menu.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.qrservice.model.enums.QrListScope;
import com.ael.algoryqrservice.qrservice.order.service.MenuWaiterOrderService;
import com.ael.algoryqrservice.qrservice.qr.dto.QrActiveRequest;
import com.ael.algoryqrservice.qrservice.qr.dto.QrActiveResponse;
import com.ael.algoryqrservice.qrservice.qr.dto.QrNameRequest;
import com.ael.algoryqrservice.qrservice.qr.dto.QrNameResponse;
import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.qr.service.QrService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import com.google.zxing.WriterException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final SessionContextService sessionContextService;
    private final SecurityUtils securityUtils;
    private final FulfillmentService fulfillmentService;
    private final EntitlementUsageService entitlementUsageService;
    private final FulfillmentPresentationMapper mapper;
    private final QrService qrService;
    private final MenuWaiterOrderService menuWaiterOrderService;

    @GetMapping("/{merchantId}/package")
    public SessionContextResponse findPackage(@PathVariable Long merchantId) {
        Long accountMerchantId = securityUtils.getCurrentMerchantId();
        if (!merchantId.equals(accountMerchantId) && !securityUtils.matchesTokenMerchant(merchantId)) {
            throw new ForbiddenException("Aktif paket bu işletmeye ait değil");
        }
        return sessionContextService.resolve(accountMerchantId);
    }

    @GetMapping("/package")
    public ResponseEntity<ActivePackageResponse> activePackage() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        try {
            return ResponseEntity.ok(
                    mapper.toActivePackageResponse(fulfillmentService.requireActivePackageView(merchantId))
            );
        } catch (ResourceNotFoundException exception) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping("/{merchantId}/package")
    public ResponseEntity<Void> assignPackage(
            @PathVariable Long merchantId,
            @Valid @RequestBody AssignPackageRequest request
    ) {
        fulfillmentService.assignPackage(merchantId, request.packageCode(), request.periodDays());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{merchantId}/active-package")
    public ResponseEntity<ActivePackageResponse> getActivePackage(@PathVariable Long merchantId) {
        return ResponseEntity.ok(mapper.toActivePackageResponse(fulfillmentService.requireActivePackageView(merchantId)));
    }

    @GetMapping("/{merchantId}/entitlements")
    public ResponseEntity<List<EntitlementResponse>> listEntitlements(@PathVariable Long merchantId) {
        return ResponseEntity.ok(
                entitlementUsageService.listActive(merchantId).stream()
                        .map(item -> mapper.toEntitlementResponse(item.entitlement(), item.productCode()))
                        .toList()
        );
    }

    @GetMapping("/{merchantId}/products/{productCode}")
    public ResponseEntity<ProductAccessResponse> productAccess(
            @PathVariable Long merchantId,
            @PathVariable String productCode
    ) {
        return ResponseEntity.ok(mapper.toProductAccess(entitlementUsageService.productAccess(merchantId, productCode)));
    }

    @PostMapping("/{merchantId}/entitlements/consume")
    public ResponseEntity<ConsumeEntitlementResponse> consume(
            @PathVariable Long merchantId,
            @Valid @RequestBody EntitlementQuantityRequest request
    ) {
        return ResponseEntity.ok(mapper.toConsume(
                entitlementUsageService.consume(merchantId, request.productCode(), request.quantity())
        ));
    }

    @PostMapping("/{merchantId}/entitlements/release")
    public ResponseEntity<ConsumeEntitlementResponse> release(
            @PathVariable Long merchantId,
            @Valid @RequestBody EntitlementQuantityRequest request
    ) {
        return ResponseEntity.ok(entitlementUsageService.release(merchantId, request.productCode(), request.quantity())
                .map(mapper::toConsume)
                .orElseGet(mapper::emptyConsume));
    }

    @GetMapping("/{merchantId}/fulfillments")
    public ResponseEntity<Page<FulfillmentResponse>> listFulfillments(
            @PathVariable Long merchantId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                fulfillmentService.listFulfillments(merchantId, pageable).map(mapper::toFulfillmentResponse)
        );
    }

    @GetMapping("/{merchantId}/qr")
    public ResponseEntity<?> getMerchantQrs(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "false") boolean includeImage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "ALL") String scope
    ) {
        return ResponseEntity.ok(
                qrService.getMerchantQrs(merchantId, includeImage, page, size, QrListScope.from(scope))
        );
    }

    @PutMapping("/qr/{qrId}")
    public ResponseEntity<?> updateQr(@PathVariable Long qrId, @RequestBody QrRequest req) throws IOException, WriterException {
        return ResponseEntity.ok(qrService.updateQr(qrId, req));
    }

    @PatchMapping("/qr/{qrId}/name")
    public ResponseEntity<QrNameResponse> updateQrName(@PathVariable Long qrId, @RequestBody QrNameRequest req) {
        return ResponseEntity.ok(qrService.updateQrName(qrId, req));
    }

    @PatchMapping("/qr/{qrId}/active")
    public ResponseEntity<QrActiveResponse> updateMenuQrActive(
            @PathVariable Long qrId,
            @RequestBody QrActiveRequest req
    ) {
        return ResponseEntity.ok(qrService.updateMenuQrActive(qrId, req));
    }

    @DeleteMapping("/qr/{qrId}")
    public ResponseEntity<String> deleteQr(@PathVariable Long qrId) {
        qrService.deleteQrByQrId(qrId);
        return ResponseEntity.ok("Deleted Success");
    }

    @DeleteMapping("/qr/{qrId}/menu")
    public ResponseEntity<String> deleteMenuQr(@PathVariable Long qrId) {
        qrService.softDeleteMenuQr(qrId);
        return ResponseEntity.ok("Deleted Success");
    }

    @GetMapping("/{merchantId}/branches/{branchId}/tables")
    public ResponseEntity<List<MenuWaiterDtos.TableOrderSummary>> listBranchTables(
            @PathVariable Long merchantId,
            @PathVariable Long branchId
    ) {
        return ResponseEntity.ok(menuWaiterOrderService.listTablesForMerchantBranch(merchantId, branchId));
    }
}
