package com.ael.algoryqrservice.service.campaign;

import com.ael.algoryqrservice.model.Campaign;
import com.ael.algoryqrservice.model.CampaignEventLog;
import com.ael.algoryqrservice.model.CampaignProgress;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.model.enums.CampaignProgressStatus;
import com.ael.algoryqrservice.repository.CampaignEventLogRepository;
import com.ael.algoryqrservice.repository.CampaignProgressRepository;
import com.ael.algoryqrservice.repository.CampaignRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CampaignEvaluationService {

    private final CampaignRepository campaignRepository;
    private final CampaignProgressRepository campaignProgressRepository;
    private final CampaignEventLogRepository campaignEventLogRepository;
    private final CampaignRewardService campaignRewardService;
    private final CampaignService campaignService;
    private final CampaignConfigSupport configSupport;
    private final MenuOrderRepository menuOrderRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public void onOrderConfirmed(MenuOrder order) {
        if (order == null || order.getCustomerId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Campaign> campaigns = campaignRepository
                .findByMenuIdAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                        order.getMenuId(),
                        com.ael.algoryqrservice.model.enums.CampaignStatus.ACTIVE,
                        now,
                        now
                );
        for (Campaign campaign : campaigns) {
            if (campaignEventLogRepository.existsByCampaignIdAndOrderId(campaign.getId(), order.getId())) {
                continue;
            }
            evaluateCampaignForOrder(campaign, order);
        }
    }

    @Transactional
    public void evaluateCampaignForOrder(Campaign campaign, MenuOrder order) {
        if (order.getCustomerId() == null) {
            return;
        }
        var config = configSupport.parseJson(campaign.getConfig());
        CampaignProgress progress = getOrCreateProgress(campaign.getId(), order.getCustomerId());
        ObjectNode state = (ObjectNode) configSupport.parseJson(progress.getState()).deepCopy();
        boolean rewardEarned = false;

        if ("STAMP_CARD".equals(campaign.getTemplateCode())) {
            int added = countCampaignProducts(order, configSupport.targetProductIds(config));
            if (added > 0) {
                int current = configSupport.currentStamps(state);
                int required = configSupport.requiredQuantity(config);
                int next = current + added;
                configSupport.setStamps(state, next);
                if (next >= required) {
                    rewardEarned = true;
                    if (configSupport.resetAfterReward(config)) {
                        configSupport.setStamps(state, next - required);
                    } else {
                        configSupport.setStamps(state, next);
                    }
                    progress.setStatus(CampaignProgressStatus.COMPLETED);
                }
            }
        } else if ("SPEND_THRESHOLD".equals(campaign.getTemplateCode())) {
            String period = configSupport.period(config);
            String periodKey = configSupport.periodKey(order.getConfirmedAt() != null
                    ? order.getConfirmedAt()
                    : LocalDateTime.now(), period);
            BigDecimal amount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            configSupport.addSpend(state, periodKey, amount);
            BigDecimal currentSpend = configSupport.currentSpend(state, periodKey);
            BigDecimal threshold = configSupport.thresholdAmount(config);
            if (currentSpend.compareTo(threshold) >= 0) {
                rewardEarned = true;
                configSupport.resetSpendPeriod(state, periodKey);
                progress.setStatus(CampaignProgressStatus.COMPLETED);
            }
        }

        progress.setState(configSupport.writeJsonObject(state));
        campaignProgressRepository.save(progress);

        campaignEventLogRepository.save(CampaignEventLog.builder()
                .campaignId(campaign.getId())
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .eventType("ORDER_CONFIRMED")
                .payload(configSupport.writeJsonObject(state))
                .createdAt(LocalDateTime.now())
                .build());

        if (rewardEarned) {
            campaignRewardService.issueReward(campaign, order.getCustomerId(), progress.getId(), order.getId(), null);
        }
    }

    @Transactional(readOnly = true)
    public CampaignDtos.PreviewResponse preview(Long menuId, CampaignDtos.PreviewRequest request) {
        Long customerId = request.getCustomerId();
        if (customerId == null) {
            customerId = securityUtils.findCurrentCustomerId().orElse(null);
        }
        Map<Long, Integer> cartCounts = new HashMap<>();
        if (request.getItems() != null) {
            for (CampaignDtos.PreviewItemRequest item : request.getItems()) {
                if (item.getProductId() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                    cartCounts.merge(item.getProductId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        List<CampaignDtos.CampaignPreviewLine> lines = new ArrayList<>();
        int totalCampaignProducts = 0;
        LocalDateTime now = LocalDateTime.now();
        List<Campaign> campaigns = campaignRepository
                .findByMenuIdAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                        menuId,
                        com.ael.algoryqrservice.model.enums.CampaignStatus.ACTIVE,
                        now,
                        now
                );

        for (Campaign campaign : campaigns) {
            var config = configSupport.parseJson(campaign.getConfig());
            CampaignProgress progress = customerId != null
                    ? campaignProgressRepository.findByCampaignIdAndCustomerId(campaign.getId(), customerId).orElse(null)
                    : null;
            ObjectNode state = progress != null
                    ? (ObjectNode) configSupport.parseJson(progress.getState()).deepCopy()
                    : configSupport.emptyObject();

            if ("STAMP_CARD".equals(campaign.getTemplateCode())) {
                List<Long> targets = configSupport.targetProductIds(config);
                int pending = countProducts(cartCounts, targets);
                totalCampaignProducts += pending;
                int current = configSupport.currentStamps(state);
                int required = configSupport.requiredQuantity(config);
                lines.add(CampaignDtos.CampaignPreviewLine.builder()
                        .campaignId(campaign.getId())
                        .campaignName(campaign.getName())
                        .templateCode(campaign.getTemplateCode())
                        .campaignProductCount(pending)
                        .pendingStamps(pending)
                        .currentStamps(current)
                        .requiredStamps(required)
                        .message(buildStampMessage(pending, current, required, customerId != null))
                        .build());
            } else if ("SPEND_THRESHOLD".equals(campaign.getTemplateCode())) {
                String period = configSupport.period(config);
                String periodKey = configSupport.periodKey(now, period);
                BigDecimal pendingSpend = sumCartAmount(menuId, cartCounts);
                BigDecimal currentSpend = configSupport.currentSpend(state, periodKey);
                BigDecimal threshold = configSupport.thresholdAmount(config);
                lines.add(CampaignDtos.CampaignPreviewLine.builder()
                        .campaignId(campaign.getId())
                        .campaignName(campaign.getName())
                        .templateCode(campaign.getTemplateCode())
                        .pendingSpend(pendingSpend)
                        .currentSpend(currentSpend)
                        .thresholdAmount(threshold)
                        .message(buildSpendMessage(pendingSpend, currentSpend, threshold, customerId != null))
                        .build());
            }
        }

        return CampaignDtos.PreviewResponse.builder()
                .lines(lines)
                .totalCampaignProducts(totalCampaignProducts)
                .loggedIn(customerId != null)
                .build();
    }

    @Transactional(readOnly = true)
    public CampaignDtos.OrderCampaignSummary summarizeOrder(MenuOrder order) {
        if (order == null || order.getItems() == null) {
            return CampaignDtos.OrderCampaignSummary.builder()
                    .campaignProductCount(0)
                    .guestOrder(order != null && order.getCustomerId() == null)
                    .rewardEligible(false)
                    .hint("")
                    .build();
        }
        Set<Long> campaignProductIds = campaignService.activeCampaignProductIds(order.getMenuId());
        int count = 0;
        for (MenuOrderItem item : order.getItems()) {
            if (campaignProductIds.contains(item.getProductId())) {
                count += item.getQuantity();
            }
        }
        boolean guest = order.getCustomerId() == null;
        String hint = guest
                ? (count > 0
                ? "Bu siparişte kampanyalı ürün var. QR okutarak giriş yapın, hak tanımlansın."
                : "Kayıtlı müşteri yok.")
                : (count > 0 ? "Kampanyalı ürün içeriyor." : "");
        return CampaignDtos.OrderCampaignSummary.builder()
                .campaignProductCount(count)
                .guestOrder(guest)
                .rewardEligible(count > 0)
                .hint(hint)
                .build();
    }

    @Transactional
    public void linkOrderToCustomer(Long menuId, Long orderId, Long customerId) {
        MenuOrder order = menuOrderRepository.findById(orderId)
                .orElseThrow(() -> new com.ael.algoryqrservice.exception.NotFoundException("Sipariş bulunamadı"));
        if (!order.getMenuId().equals(menuId)) {
            throw new com.ael.algoryqrservice.exception.BadRequestException("Sipariş menü ile eşleşmiyor");
        }
        if (order.getCustomerId() != null) {
            throw new com.ael.algoryqrservice.exception.BadRequestException("Sipariş zaten bir müşteriye bağlı");
        }
        order.setCustomerId(customerId);
        menuOrderRepository.save(order);
        if (order.getStatus() == com.ael.algoryqrservice.model.enums.MenuOrderStatus.CONFIRMED) {
            onOrderConfirmed(order);
        }
    }

    private CampaignProgress getOrCreateProgress(Long campaignId, Long customerId) {
        return campaignProgressRepository.findByCampaignIdAndCustomerId(campaignId, customerId)
                .orElseGet(() -> campaignProgressRepository.save(CampaignProgress.builder()
                        .campaignId(campaignId)
                        .customerId(customerId)
                        .state("{}")
                        .status(CampaignProgressStatus.IN_PROGRESS)
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    private int countCampaignProducts(MenuOrder order, List<Long> targetProductIds) {
        if (order.getItems() == null || targetProductIds.isEmpty()) {
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

    private int countProducts(Map<Long, Integer> cartCounts, List<Long> targetProductIds) {
        int count = 0;
        for (Long productId : targetProductIds) {
            count += cartCounts.getOrDefault(productId, 0);
        }
        return count;
    }

    private BigDecimal sumCartAmount(Long menuId, Map<Long, Integer> cartCounts) {
        BigDecimal total = BigDecimal.ZERO;
        return total;
    }

    private String buildStampMessage(int pending, int current, int required, boolean loggedIn) {
        if (pending <= 0) {
            return loggedIn
                    ? "Kampanya ilerlemeniz: " + current + "/" + required
                    : "Kampanyadan yararlanmak için giriş yapın.";
        }
        if (!loggedIn) {
            return "Bu siparişte " + pending + " kampanyalı ürün var. Giriş yaparsanız onay sonrası "
                    + pending + " damga kazanırsınız.";
        }
        return "Bu siparişte " + pending + " kampanyalı ürün var. Onay sonrası "
                + (current + pending) + "/" + required + " damga.";
    }

    private String buildSpendMessage(
            BigDecimal pendingSpend,
            BigDecimal currentSpend,
            BigDecimal threshold,
            boolean loggedIn
    ) {
        if (!loggedIn) {
            return "Harcama kampanyasından yararlanmak için giriş yapın.";
        }
        return "Bu sipariş onaylanırsa harcama ilerlemeniz "
                + currentSpend.add(pendingSpend) + "/" + threshold + " TL olur.";
    }
}
