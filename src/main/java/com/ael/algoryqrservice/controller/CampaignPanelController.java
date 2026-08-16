package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.campaign.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/waiter-panel")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.WAITER_PANEL_OWNER)
public class CampaignPanelController {

    private final CampaignService campaignService;

    @GetMapping("/campaigns/templates")
    public ResponseEntity<List<CampaignDtos.TemplateResponse>> listTemplates() {
        return ResponseEntity.ok(campaignService.listTemplates());
    }

    @GetMapping("/menu/{menuId}/campaigns")
    public ResponseEntity<List<CampaignDtos.CampaignResponse>> listCampaigns(@PathVariable Long menuId) {
        return ResponseEntity.ok(campaignService.listCampaigns(menuId));
    }

    @GetMapping("/menu/{menuId}/campaigns/{campaignId}")
    public ResponseEntity<CampaignDtos.CampaignResponse> getCampaign(
            @PathVariable Long menuId,
            @PathVariable Long campaignId
    ) {
        return ResponseEntity.ok(campaignService.getCampaign(menuId, campaignId));
    }

    @GetMapping("/menu/{menuId}/campaigns/{campaignId}/winners")
    public ResponseEntity<CampaignDtos.WinnerPageResponse> listWinners(
            @PathVariable Long menuId,
            @PathVariable Long campaignId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(campaignService.listWinners(menuId, campaignId, q, page, size));
    }

    @PostMapping("/menu/{menuId}/campaigns")
    public ResponseEntity<CampaignDtos.CampaignResponse> createCampaign(
            @PathVariable Long menuId,
            @Valid @RequestBody CampaignDtos.CreateCampaignRequest request
    ) {
        return ResponseEntity.status(201).body(campaignService.createCampaign(menuId, request));
    }

    @PutMapping("/menu/{menuId}/campaigns/{campaignId}")
    public ResponseEntity<CampaignDtos.CampaignResponse> updateCampaign(
            @PathVariable Long menuId,
            @PathVariable Long campaignId,
            @RequestBody CampaignDtos.UpdateCampaignRequest request
    ) {
        return ResponseEntity.ok(campaignService.updateCampaign(menuId, campaignId, request));
    }

    @PostMapping("/menu/{menuId}/campaigns/{campaignId}/activate")
    public ResponseEntity<CampaignDtos.CampaignResponse> activateCampaign(
            @PathVariable Long menuId,
            @PathVariable Long campaignId
    ) {
        return ResponseEntity.ok(campaignService.activateCampaign(menuId, campaignId));
    }

    @PostMapping("/menu/{menuId}/campaigns/{campaignId}/pause")
    public ResponseEntity<CampaignDtos.CampaignResponse> pauseCampaign(
            @PathVariable Long menuId,
            @PathVariable Long campaignId
    ) {
        return ResponseEntity.ok(campaignService.pauseCampaign(menuId, campaignId));
    }
}
