package com.ael.algoryqrservice.qrservice.billing.controller;

import com.ael.algoryqrservice.qrservice.billing.dto.BillingAddressDtos;
import com.ael.algoryqrservice.qrservice.billing.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.qrservice.billing.service.BillingAddressService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/billing-addresses")
@RequiredArgsConstructor
public class BillingAddressController {
    private final BillingAddressService service;
    private final SecurityUtils securityUtils;

    @GetMapping
    public BillingAddressPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        return service.list(merchantId(), page, size);
    }

    @GetMapping("/{id}")
    public BillingAddressDtos.Response get(@PathVariable Long id) {
        return service.get(merchantId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillingAddressDtos.Response create(@Valid @RequestBody BillingAddressDtos.Request request) {
        return service.create(merchantId(), request);
    }

    @PutMapping("/{id}")
    public BillingAddressDtos.Response update(
            @PathVariable Long id,
            @Valid @RequestBody BillingAddressDtos.Request request
    ) {
        return service.update(merchantId(), id, request);
    }

    @PutMapping("/{id}/default")
    public BillingAddressDtos.Response makeDefault(@PathVariable Long id) {
        return service.makeDefault(merchantId(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(merchantId(), id);
        return ResponseEntity.noContent().build();
    }

    private Long merchantId() {
        return securityUtils.getCurrentMerchantId();
    }
}
