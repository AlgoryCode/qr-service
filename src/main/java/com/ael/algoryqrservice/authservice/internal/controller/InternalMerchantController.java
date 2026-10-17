package com.ael.algoryqrservice.authservice.internal.controller;

import com.ael.algoryqrservice.authservice.application.auth.FindMerchantUseCase;
import com.ael.algoryqrservice.authservice.application.auth.SoftDeleteMerchantUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/merchants")
@RequiredArgsConstructor
public class InternalMerchantController {

    private final SoftDeleteMerchantUseCase softDeleteMerchantUseCase;
    private final FindMerchantUseCase findMerchantUseCase;

    @GetMapping("/{merchantId}")
    public ResponseEntity<FindMerchantUseCase.MerchantResponse> findMerchant(@PathVariable Long merchantId) {
        return ResponseEntity.ok(findMerchantUseCase.findMerchant(merchantId));
    }

    @PostMapping("/{merchantId}/soft-delete")
    public ResponseEntity<Void> softDelete(@PathVariable Long merchantId) {
        softDeleteMerchantUseCase.softDelete(merchantId);
        return ResponseEntity.noContent().build();
    }
}
