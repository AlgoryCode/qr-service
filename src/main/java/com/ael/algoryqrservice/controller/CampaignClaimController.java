package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.service.campaign.CampaignRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaign/claim")
@RequiredArgsConstructor
public class CampaignClaimController {

    private final CampaignRewardService campaignRewardService;

    @GetMapping
    public ResponseEntity<CampaignDtos.ClaimInfoResponse> getClaimInfo(@RequestParam("c") String token) {
        return ResponseEntity.ok(campaignRewardService.getClaimInfo(token));
    }

    @PostMapping
    public ResponseEntity<CampaignDtos.ClaimResultResponse> claim(@RequestParam("c") String token) {
        return ResponseEntity.ok(campaignRewardService.claimReward(token));
    }
}
