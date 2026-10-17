package com.ael.algoryqrservice.fulfillmentservice.presentation.controller;

import com.ael.algoryqrservice.fulfillmentservice.application.service.FulfillmentService;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.ResourceNotFoundException;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ActivePackageResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.mapper.FulfillmentPresentationMapper;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantPackageController {

    private final FulfillmentService fulfillmentService;
    private final FulfillmentPresentationMapper mapper;
    private final SecurityUtils securityUtils;

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
}
