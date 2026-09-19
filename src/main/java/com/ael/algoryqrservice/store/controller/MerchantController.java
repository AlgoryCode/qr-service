package com.ael.algoryqrservice.store.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.store.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/me")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.ONLINE_ORDER_OWNER)
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping
    public ResponseEntity<StoreDtos.MerchantResponse> getMine() {
        return merchantService.findMine()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PatchMapping
    public ResponseEntity<StoreDtos.MerchantResponse> update(
            @Valid @RequestBody StoreDtos.UpdateMerchantRequest request
    ) {
        return ResponseEntity.ok(merchantService.update(request));
    }
}
