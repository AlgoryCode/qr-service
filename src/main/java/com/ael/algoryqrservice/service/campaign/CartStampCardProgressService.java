package com.ael.algoryqrservice.service.campaign;

import com.ael.algoryqrservice.model.Campaign;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.model.enums.CampaignStatus;
import com.ael.algoryqrservice.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CartStampCardProgressService {

    private final CampaignRepository campaignRepository;
    private final CampaignConfigSupport configSupport;

    /**
     * For each active STAMP_CARD campaign on the order menu, report qualifying cart qty
     * vs requiredQuantity and whether the cart alone already earns the stamp target.
     */
    @Transactional(readOnly = true)
    public List<CampaignDtos.StampCardProgress> forOrder(MenuOrder order) {
        if (order == null || order.getMenuId() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<Campaign> campaigns = campaignRepository
                .findByMenuIdAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                        order.getMenuId(),
                        CampaignStatus.ACTIVE,
                        now,
                        now
                );
        List<CampaignDtos.StampCardProgress> lines = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            if (!"STAMP_CARD".equals(campaign.getTemplateCode())) {
                continue;
            }
            var config = configSupport.parseJson(campaign.getConfig());
            int required = configSupport.requiredQuantity(config);
            int current = countQualifying(order, configSupport.targetProductIds(config));
            lines.add(CampaignDtos.StampCardProgress.builder()
                    .campaignId(campaign.getId())
                    .campaignName(campaign.getName())
                    .currentQuantity(current)
                    .requiredQuantity(required)
                    .earned(current >= required)
                    .build());
        }
        return lines;
    }

    private int countQualifying(MenuOrder order, List<Long> targetProductIds) {
        if (order.getItems() == null || targetProductIds == null || targetProductIds.isEmpty()) {
            return 0;
        }
        Set<Long> targets = new HashSet<>(targetProductIds);
        int count = 0;
        for (MenuOrderItem item : order.getItems()) {
            if (targets.contains(item.getProductId())) {
                count += item.getQuantity();
            }
        }
        return count;
    }
}
