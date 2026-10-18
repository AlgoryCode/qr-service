package com.ael.algoryqrservice.fulfillmentservice.presentation.controller;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.application.service.PackageCatalogService;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.FulfillmentResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.PackageResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.mapper.FulfillmentPresentationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;
    private final PackageCatalogService packageCatalogService;
    private final FulfillmentPresentationMapper mapper;

    @GetMapping("/fulfillments/{fulfillmentId}")
    public ResponseEntity<FulfillmentResponse> getFulfillment(@PathVariable Long fulfillmentId) {
        return ResponseEntity.ok(mapper.toFulfillmentResponse(fulfillmentService.requireFulfillment(fulfillmentId)));
    }

    @GetMapping("/packages")
    public ResponseEntity<Page<PackageResponse>> listPackages(@PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(packageCatalogService.listActive(pageable).map(mapper::toPackageResponse));
    }

    @GetMapping("/packages/purchasable")
    public ResponseEntity<Page<PackageResponse>> listPurchasablePackages(
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(packageCatalogService.listPurchasable(pageable).map(mapper::toPackageResponse));
    }

    @GetMapping("/packages/{code}")
    public ResponseEntity<PackageResponse> getPackage(@PathVariable String code) {
        return ResponseEntity.ok(mapper.toPackageResponse(packageCatalogService.requireByCode(code)));
    }
}
