package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.CampaignReward;
import com.ael.algoryqrservice.model.enums.CampaignRewardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignRewardRepository extends JpaRepository<CampaignReward, Long> {
    List<CampaignReward> findByCustomerIdAndCampaignIdAndStatus(
            Long customerId,
            Long campaignId,
            CampaignRewardStatus status
    );

    Optional<CampaignReward> findByIdAndCustomerId(Long id, Long customerId);

    @Query("""
            SELECT r FROM CampaignReward r
            WHERE r.campaignId = :campaignId
            AND (
                :query IS NULL
                OR EXISTS (
                    SELECT 1 FROM Customer c
                    WHERE c.id = r.customerId
                    AND (
                        LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(c.lastName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    )
                )
            )
            """)
    Page<CampaignReward> searchWinners(
            @Param("campaignId") Long campaignId,
            @Param("query") String query,
            Pageable pageable
    );
}
