package com.ael.algoryqrservice.service.campaign;

import com.ael.algoryqrservice.model.Campaign;
import com.ael.algoryqrservice.model.CampaignReward;
import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.model.enums.CampaignRewardStatus;
import com.ael.algoryqrservice.repository.CampaignRepository;
import com.ael.algoryqrservice.repository.CampaignRewardRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerCampaignRewardService {

    private final CampaignRewardRepository campaignRewardRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignConfigSupport configSupport;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<CampaignDtos.CustomerRewardResponse> listMyRewards() {
        Long customerId = securityUtils.getCurrentCustomerId();
        List<CampaignReward> rewards = campaignRewardRepository.findByCustomerIdAndStatusInOrderByIssuedAtDesc(
                customerId,
                List.of(CampaignRewardStatus.AVAILABLE, CampaignRewardStatus.REDEEMED)
        );
        if (rewards.isEmpty()) {
            return List.of();
        }
        List<Long> campaignIds = rewards.stream().map(CampaignReward::getCampaignId).distinct().toList();
        Map<Long, Campaign> campaigns = campaignRepository.findAllById(campaignIds).stream()
                .collect(Collectors.toMap(Campaign::getId, Function.identity()));
        return rewards.stream()
                .map(reward -> toResponse(reward, campaigns.get(reward.getCampaignId())))
                .toList();
    }

    private CampaignDtos.CustomerRewardResponse toResponse(CampaignReward reward, Campaign campaign) {
        return CampaignDtos.CustomerRewardResponse.builder()
                .rewardId(reward.getId())
                .campaignId(reward.getCampaignId())
                .campaignName(campaign != null ? campaign.getName() : null)
                .rewardType(reward.getRewardType())
                .rewardPayload(configSupport.parseJsonMap(reward.getRewardPayload()))
                .status(reward.getStatus())
                .issuedAt(reward.getIssuedAt())
                .redeemedAt(reward.getRedeemedAt())
                .build();
    }
}
