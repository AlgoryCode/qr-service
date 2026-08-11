package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.BillingAddressDtos;
import com.ael.algoryqrservice.model.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.service.BillingAddressService;
import com.ael.algoryqrservice.util.SecurityUtils;
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
        return service.list(userId(), page, size);
    }

    @GetMapping("/{id}")
    public BillingAddressDtos.Response get(@PathVariable Long id) {
        return service.get(userId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillingAddressDtos.Response create(@Valid @RequestBody BillingAddressDtos.Request request) {
        return service.create(userId(), request);
    }

    @PutMapping("/{id}")
    public BillingAddressDtos.Response update(
            @PathVariable Long id,
            @Valid @RequestBody BillingAddressDtos.Request request
    ) {
        return service.update(userId(), id, request);
    }

    @PutMapping("/{id}/default")
    public BillingAddressDtos.Response makeDefault(@PathVariable Long id) {
        return service.makeDefault(userId(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(userId(), id);
        return ResponseEntity.noContent().build();
    }

    private Long userId() {
        return securityUtils.getCurrentUser().getId();
    }
}
