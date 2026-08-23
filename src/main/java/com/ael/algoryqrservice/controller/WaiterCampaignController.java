package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.service.campaign.CampaignManualGrantService;
import com.ael.algoryqrservice.service.campaign.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/waiter/campaigns")
@RequiredArgsConstructor
public class WaiterCampaignController {

    private final CampaignService campaignService;
    private final CampaignManualGrantService campaignManualGrantService;

    @GetMapping("/active")
    public ResponseEntity<List<CampaignDtos.ActiveCampaignResponse>> listActive() {
        return ResponseEntity.ok(campaignService.listActiveCampaignsForCurrentWaiter());
    }

    @GetMapping("/customers")
    public ResponseEntity<CampaignDtos.WaiterCustomerLookupResponse> lookupCustomer(
            @RequestParam Long menuId,
            @RequestParam String email
    ) {
        return ResponseEntity.ok(campaignManualGrantService.lookupCustomer(menuId, email));
    }

    @PostMapping("/grant")
    public ResponseEntity<CampaignDtos.ManualGrantResponse> grant(
            @RequestParam Long menuId,
            @Valid @RequestBody CampaignDtos.ManualGrantRequest request
    ) {
        return ResponseEntity.ok(campaignManualGrantService.grant(menuId, request));
    }
}
