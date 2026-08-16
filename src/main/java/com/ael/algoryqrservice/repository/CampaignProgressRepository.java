package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.CampaignProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignProgressRepository extends JpaRepository<CampaignProgress, Long> {
    Optional<CampaignProgress> findByCampaignIdAndCustomerId(Long campaignId, Long customerId);

    List<CampaignProgress> findByCustomerIdAndCampaignIdIn(Long customerId, List<Long> campaignIds);
}
