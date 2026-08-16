package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.CampaignTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignTemplateRepository extends JpaRepository<CampaignTemplate, Long> {
    Optional<CampaignTemplate> findByCode(String code);

    List<CampaignTemplate> findAllByOrderBySortOrderAsc();
}
