package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.CampaignRewardClaim;
import com.ael.algoryqrservice.model.enums.CampaignClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignRewardClaimRepository extends JpaRepository<CampaignRewardClaim, Long> {
    Optional<CampaignRewardClaim> findByToken(String token);

    List<CampaignRewardClaim> findByOrderIdAndStatus(Long orderId, CampaignClaimStatus status);
}
