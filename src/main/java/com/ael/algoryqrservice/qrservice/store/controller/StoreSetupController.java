package com.ael.algoryqrservice.qrservice.store.controller;

import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.RequiresFeature;
import com.ael.algoryqrservice.qrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.qrservice.store.service.MerchantSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store/setup")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.ONLINE_ORDER)
public class StoreSetupController {

    private final MerchantSetupService merchantSetupService;

    @GetMapping("/prefill")
    public ResponseEntity<StoreDtos.SetupPrefillResponse> prefill() {
        return ResponseEntity.ok(merchantSetupService.prefill());
    }

    @PostMapping
    public ResponseEntity<StoreDtos.MerchantResponse> setup(@Valid @RequestBody StoreDtos.SetupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantSetupService.setup(request));
    }
}
