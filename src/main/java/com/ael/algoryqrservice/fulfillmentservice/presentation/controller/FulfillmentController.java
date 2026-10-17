package com.ael.algoryqrservice.fulfillmentservice.presentation.controller;

import com.ael.algoryqrservice.fulfillmentservice.application.service.EntitlementUsageService;
import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.application.service.PackageCatalogService;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.request.AssignPackageRequest;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.request.EntitlementQuantityRequest;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ActivePackageResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ConsumeEntitlementResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.EntitlementResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.FulfillmentResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.PackageResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ProductAccessResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.mapper.FulfillmentPresentationMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;
    private final PackageCatalogService packageCatalogService;
    private final EntitlementUsageService entitlementUsageService;
    private final FulfillmentPresentationMapper mapper;

    @GetMapping("/users/{merchantId}/package")
    public ResponseEntity<ActivePackageResponse> getAssignedPackage(@PathVariable Long merchantId) {
        return ResponseEntity.ok(mapper.toActivePackageResponse(fulfillmentService.requireActivePackageView(merchantId)));
    }

    @PostMapping("/users/{merchantId}/package")
    public ResponseEntity<Void> assignPackage(
            @PathVariable Long merchantId,
            @Valid @RequestBody AssignPackageRequest request
    ) {
        fulfillmentService.assignPackage(merchantId, request.packageCode(), request.periodDays());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/users/{merchantId}/active-package")
    public ResponseEntity<ActivePackageResponse> getActivePackage(@PathVariable Long merchantId) {
        return ResponseEntity.ok(mapper.toActivePackageResponse(fulfillmentService.requireActivePackageView(merchantId)));
    }

    @GetMapping("/users/{merchantId}/entitlements")
    public ResponseEntity<List<EntitlementResponse>> listEntitlements(@PathVariable Long merchantId) {
        return ResponseEntity.ok(
                entitlementUsageService.listActive(merchantId).stream()
                        .map(item -> mapper.toEntitlementResponse(item.entitlement(), item.productCode()))
                        .toList()
        );
    }

    @GetMapping("/users/{merchantId}/products/{productCode}")
    public ResponseEntity<ProductAccessResponse> productAccess(
            @PathVariable Long merchantId,
            @PathVariable String productCode
    ) {
        return ResponseEntity.ok(mapper.toProductAccess(entitlementUsageService.productAccess(merchantId, productCode)));
    }

    @PostMapping("/users/{merchantId}/entitlements/consume")
    public ResponseEntity<ConsumeEntitlementResponse> consume(
            @PathVariable Long merchantId,
            @Valid @RequestBody EntitlementQuantityRequest request
    ) {
        return ResponseEntity.ok(mapper.toConsume(
                entitlementUsageService.consume(merchantId, request.productCode(), request.quantity())
        ));
    }

    @PostMapping("/users/{merchantId}/entitlements/release")
    public ResponseEntity<ConsumeEntitlementResponse> release(
            @PathVariable Long merchantId,
            @Valid @RequestBody EntitlementQuantityRequest request
    ) {
        return ResponseEntity.ok(entitlementUsageService.release(merchantId, request.productCode(), request.quantity())
                .map(mapper::toConsume)
                .orElseGet(mapper::emptyConsume));
    }

    @GetMapping("/users/{merchantId}/fulfillments")
    public ResponseEntity<Page<FulfillmentResponse>> listFulfillments(
            @PathVariable Long merchantId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                fulfillmentService.listFulfillments(merchantId, pageable).map(mapper::toFulfillmentResponse)
        );
    }

    @GetMapping("/fulfillments/{fulfillmentId}")    public ResponseEntity<FulfillmentResponse> getFulfillment(@PathVariable Long fulfillmentId) {
        return ResponseEntity.ok(mapper.toFulfillmentResponse(fulfillmentService.requireFulfillment(fulfillmentId)));
    }

    @GetMapping("/packages")    public ResponseEntity<Page<PackageResponse>> listPackages(@PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(packageCatalogService.listActive(pageable).map(mapper::toPackageResponse));
    }

    @GetMapping("/packages/purchasable")    public ResponseEntity<Page<PackageResponse>> listPurchasablePackages(
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(packageCatalogService.listPurchasable(pageable).map(mapper::toPackageResponse));
    }

    @GetMapping("/packages/{code}")    public ResponseEntity<PackageResponse> getPackage(@PathVariable String code) {
        return ResponseEntity.ok(mapper.toPackageResponse(packageCatalogService.requireByCode(code)));
    }
}
