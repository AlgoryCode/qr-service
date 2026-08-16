package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.campaign.CampaignEvaluationService;
import com.ael.algoryqrservice.service.campaign.CampaignRewardService;
import com.ael.algoryqrservice.service.campaign.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/menu/public/id/{qrId}/campaigns")
@RequiredArgsConstructor
public class PublicCampaignController {

    private final CampaignService campaignService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final CampaignRewardService campaignRewardService;
    private final MenuRepository menuRepository;

    @GetMapping("/active")
    public ResponseEntity<List<CampaignDtos.ActiveCampaignResponse>> listActive(@PathVariable Long qrId) {
        Menu menu = requireMenu(qrId);
        return ResponseEntity.ok(campaignService.listActiveCampaigns(menu.getMenuId()));
    }

    @GetMapping("/product-ids")
    public ResponseEntity<Set<Long>> listCampaignProductIds(@PathVariable Long qrId) {
        Menu menu = requireMenu(qrId);
        return ResponseEntity.ok(campaignService.activeCampaignProductIds(menu.getMenuId()));
    }

    @PostMapping("/preview")
    public ResponseEntity<CampaignDtos.PreviewResponse> preview(
            @PathVariable Long qrId,
            @RequestBody(required = false) CampaignDtos.PreviewRequest request
    ) {
        Menu menu = requireMenu(qrId);
        CampaignDtos.PreviewRequest body = request != null ? request : new CampaignDtos.PreviewRequest();
        return ResponseEntity.ok(campaignEvaluationService.preview(menu.getMenuId(), body));
    }

    @PostMapping("/rewards/{orderId}/produce")
    public ResponseEntity<CampaignDtos.ProduceRewardResponse> produceReward(
            @PathVariable Long qrId,
            @PathVariable Long orderId
    ) {
        Menu menu = requireMenu(qrId);
        return ResponseEntity.ok(campaignRewardService.produceReward(menu.getMenuId(), orderId));
    }

    private Menu requireMenu(Long qrId) {
        return menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(qrId)
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
    }
}
