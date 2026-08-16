package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.Campaign;
import com.ael.algoryqrservice.model.enums.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByMenuIdOrderByCreatedAtDesc(Long menuId);

    List<Campaign> findByMenuIdAndStatusOrderByCreatedAtDesc(Long menuId, CampaignStatus status);

    List<Campaign> findByMenuIdAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
            Long menuId,
            CampaignStatus status,
            LocalDateTime now,
            LocalDateTime nowAgain
    );

    Optional<Campaign> findByIdAndMenuId(Long id, Long menuId);
}
