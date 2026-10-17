package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.CampaignEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignEventLogRepository extends JpaRepository<CampaignEventLog, Long> {
    boolean existsByCampaignIdAndOrderId(Long campaignId, Long orderId);
}
