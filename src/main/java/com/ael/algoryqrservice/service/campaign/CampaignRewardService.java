package com.ael.algoryqrservice.service.campaign;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Campaign;
import com.ael.algoryqrservice.model.CampaignReward;
import com.ael.algoryqrservice.model.CampaignRewardClaim;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.model.enums.CampaignClaimStatus;
import com.ael.algoryqrservice.model.enums.CampaignProgressStatus;
import com.ael.algoryqrservice.model.enums.CampaignRewardStatus;
import com.ael.algoryqrservice.repository.CampaignProgressRepository;
import com.ael.algoryqrservice.repository.CampaignRewardClaimRepository;
import com.ael.algoryqrservice.repository.CampaignRewardRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaignRewardService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 24;
    private static final int CLAIM_HOURS = 48;

    private final CampaignRewardRepository campaignRewardRepository;
    private final CampaignRewardClaimRepository campaignRewardClaimRepository;
    private final CampaignProgressRepository campaignProgressRepository;
    private final MenuOrderRepository menuOrderRepository;
    private final CampaignService campaignService;
    private final CampaignConfigSupport configSupport;
    private final SecurityUtils securityUtils;
    private final AppProperties appProperties;

    @Transactional
    public CampaignReward issueReward(
            Campaign campaign,
            Long customerId,
            Long progressId,
            Long orderId,
            Long existingRewardId
    ) {
        if (existingRewardId != null) {
            return campaignRewardRepository.findById(existingRewardId)
                    .orElseThrow(() -> new NotFoundException("Ödül bulunamadı"));
        }
        JsonNode rewardConfig = configSupport.rewardNode(configSupport.parseJson(campaign.getConfig()));
        CampaignReward reward = campaignRewardRepository.save(CampaignReward.builder()
                .campaignId(campaign.getId())
                .customerId(customerId)
                .progressId(progressId)
                .orderId(orderId)
                .rewardType(configSupport.rewardType(rewardConfig))
                .rewardPayload(configSupport.writeJson(rewardConfig))
                .status(CampaignRewardStatus.AVAILABLE)
                .issuedAt(LocalDateTime.now())
                .build());
        if (progressId != null) {
            campaignProgressRepository.findById(progressId).ifPresent(progress -> {
                progress.setStatus(CampaignProgressStatus.REWARDED);
                campaignProgressRepository.save(progress);
            });
        }
        return reward;
    }

    @Transactional
    public CampaignDtos.ProduceRewardResponse produceReward(Long menuId, Long orderId) {
        MenuOrder order = menuOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Sipariş bulunamadı"));
        if (!order.getMenuId().equals(menuId)) {
            throw new BadRequestException("Sipariş menü ile eşleşmiyor");
        }
        if (order.getStatus() != com.ael.algoryqrservice.model.enums.MenuOrderStatus.CONFIRMED) {
            throw new BadRequestException("Ödül yalnızca onaylanmış siparişler için üretilebilir");
        }

        Long customerId = order.getCustomerId();
        if (customerId == null) {
            customerId = securityUtils.findCurrentCustomerId().orElse(null);
        }

        List<CampaignRewardClaim> existingClaims = campaignRewardClaimRepository
                .findByOrderIdAndStatus(orderId, CampaignClaimStatus.PENDING);
        if (!existingClaims.isEmpty()) {
            return buildClaimResponse(existingClaims.get(0), false);
        }

        Campaign campaign = resolveCampaignForProduce(order);
        if (campaign == null) {
            throw new BadRequestException("Bu sipariş için üretilebilir ödül bulunamadı");
        }

        if (customerId != null) {
            List<CampaignReward> existing = campaignRewardRepository
                    .findByCustomerIdAndCampaignIdAndStatus(customerId, campaign.getId(), CampaignRewardStatus.AVAILABLE);
            if (!existing.isEmpty()) {
                CampaignReward reward = existing.get(0);
                return CampaignDtos.ProduceRewardResponse.builder()
                        .autoAssigned(true)
                        .rewardId(reward.getId())
                        .message("Ödül hesabınıza tanımlandı.")
                        .build();
            }
            CampaignReward reward = issueReward(campaign, customerId, null, orderId, null);
            return CampaignDtos.ProduceRewardResponse.builder()
                    .autoAssigned(true)
                    .rewardId(reward.getId())
                    .message("Ödül hesabınıza tanımlandı.")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        CampaignRewardClaim claim = campaignRewardClaimRepository.save(CampaignRewardClaim.builder()
                .token(generateToken())
                .campaignId(campaign.getId())
                .orderId(orderId)
                .menuId(menuId)
                .status(CampaignClaimStatus.PENDING)
                .expiresAt(now.plusHours(CLAIM_HOURS))
                .createdAt(now)
                .build());
        return buildClaimResponse(claim, false);
    }

    @Transactional(readOnly = true)
    public CampaignDtos.ClaimInfoResponse getClaimInfo(String token) {
        CampaignRewardClaim claim = requireClaim(token);
        Campaign campaign = campaignService.requireCampaign(claim.getMenuId(), claim.getCampaignId());
        if (claim.getStatus() == CampaignClaimStatus.CLAIMED) {
            return CampaignDtos.ClaimInfoResponse.builder()
                    .status("CLAIMED")
                    .campaignName(campaign.getName())
                    .message("Bu ödül zaten tanımlanmış.")
                    .requiresLogin(false)
                    .alreadyClaimed(true)
                    .build();
        }
        if (claim.getExpiresAt().isBefore(LocalDateTime.now())) {
            return CampaignDtos.ClaimInfoResponse.builder()
                    .status("EXPIRED")
                    .campaignName(campaign.getName())
                    .message("Claim süresi dolmuş.")
                    .requiresLogin(true)
                    .alreadyClaimed(false)
                    .build();
        }
        return CampaignDtos.ClaimInfoResponse.builder()
                .status("PENDING")
                .campaignName(campaign.getName())
                .message("Ödülünüzü hesabınıza tanımlamak için giriş yapın.")
                .requiresLogin(true)
                .alreadyClaimed(false)
                .build();
    }

    @Transactional
    public CampaignDtos.ClaimResultResponse claimReward(String token) {
        CampaignRewardClaim claim = requireClaim(token);
        if (claim.getStatus() == CampaignClaimStatus.CLAIMED) {
            throw new BadRequestException("Bu ödül zaten tanımlanmış");
        }
        if (claim.getExpiresAt().isBefore(LocalDateTime.now())) {
            claim.setStatus(CampaignClaimStatus.EXPIRED);
            campaignRewardClaimRepository.save(claim);
            throw new BadRequestException("Claim süresi dolmuş");
        }
        Long customerId = securityUtils.getCurrentCustomerId();
        Campaign campaign = campaignService.requireActiveCampaign(claim.getMenuId(), claim.getCampaignId());
        MenuOrder order = menuOrderRepository.findById(claim.getOrderId())
                .orElseThrow(() -> new NotFoundException("Sipariş bulunamadı"));
        if (order.getCustomerId() == null) {
            order.setCustomerId(customerId);
            menuOrderRepository.save(order);
        } else if (!order.getCustomerId().equals(customerId)) {
            throw new BadRequestException("Bu ödül başka bir hesaba ait");
        }
        CampaignReward reward = issueReward(campaign, customerId, null, order.getId(), claim.getRewardId());
        claim.setCustomerId(customerId);
        claim.setRewardId(reward.getId());
        claim.setStatus(CampaignClaimStatus.CLAIMED);
        claim.setClaimedAt(LocalDateTime.now());
        campaignRewardClaimRepository.save(claim);
        return CampaignDtos.ClaimResultResponse.builder()
                .rewardId(reward.getId())
                .message("Ödül hesabınıza tanımlandı.")
                .build();
    }

    private Campaign resolveCampaignForProduce(MenuOrder order) {
        return campaignService.listActiveCampaigns(order.getMenuId()).stream()
                .findFirst()
                .map(active -> campaignService.requireCampaign(order.getMenuId(), active.getId()))
                .orElse(null);
    }

    private CampaignRewardClaim requireClaim(String token) {
        return campaignRewardClaimRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Claim bulunamadı"));
    }

    private CampaignDtos.ProduceRewardResponse buildClaimResponse(CampaignRewardClaim claim, boolean autoAssigned) {
        return CampaignDtos.ProduceRewardResponse.builder()
                .autoAssigned(autoAssigned)
                .claimToken(claim.getToken())
                .claimUrl(buildClaimUrl(claim.getToken()))
                .message("QR okutarak giriş yapın, hak tanımlansın.")
                .build();
    }

    private String buildClaimUrl(String token) {
        String base = appProperties.getUrl();
        if (base == null || base.isBlank()) {
            return "/reward/claim?c=" + token;
        }
        return base.replaceAll("/$", "") + "/reward/claim?c=" + token;
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
