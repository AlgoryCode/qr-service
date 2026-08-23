package com.ael.algoryqrservice.service.campaign;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Campaign;
import com.ael.algoryqrservice.model.CampaignManualGrant;
import com.ael.algoryqrservice.model.CampaignProgress;
import com.ael.algoryqrservice.model.CampaignReward;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.CustomerMembership;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.model.enums.CampaignManualGrantAction;
import com.ael.algoryqrservice.model.enums.CampaignProgressStatus;
import com.ael.algoryqrservice.model.enums.MembershipStatus;
import com.ael.algoryqrservice.repository.CampaignManualGrantRepository;
import com.ael.algoryqrservice.repository.CampaignProgressRepository;
import com.ael.algoryqrservice.repository.CustomerMembershipRepository;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.service.WaiterAccessService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CampaignManualGrantService {

    private final CampaignManualGrantRepository campaignManualGrantRepository;
    private final CampaignProgressRepository campaignProgressRepository;
    private final CustomerRepository customerRepository;
    private final CustomerMembershipRepository customerMembershipRepository;
    private final CampaignService campaignService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final CampaignRewardService campaignRewardService;
    private final CampaignConfigSupport configSupport;
    private final WaiterAccessService waiterAccessService;

    @Transactional(readOnly = true)
    public CampaignDtos.WaiterCustomerLookupResponse lookupCustomer(Long menuId, String email) {
        requireWaiterForMenu(menuId);
        String normalized = normalizeEmail(email);
        Customer customer = customerRepository.findByEmail(normalized)
                .orElseThrow(() -> new NotFoundException("Bu e-posta ile kayıtlı müşteri bulunamadı"));
        boolean member = customerMembershipRepository
                .findByCustomerIdAndMenuId(customer.getId(), menuId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .orElse(false);
        return CampaignDtos.WaiterCustomerLookupResponse.builder()
                .customerId(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .member(member)
                .build();
    }

    @Transactional
    public CampaignDtos.ManualGrantResponse grant(Long menuId, CampaignDtos.ManualGrantRequest request) {
        MenuWaiter waiter = requireWaiterForMenu(menuId);
        Campaign campaign = campaignService.requireActiveCampaign(menuId, request.getCampaignId());
        String normalized = normalizeEmail(request.getEmail());
        Customer customer = customerRepository.findByEmail(normalized)
                .orElseThrow(() -> new BadRequestException("Müşteri bulunamadı. Önce üye olmalı."));
        ensureMembership(customer.getId(), menuId, campaign.getBusinessId());

        CampaignProgress progress = campaignProgressRepository
                .findByCampaignIdAndCustomerId(campaign.getId(), customer.getId())
                .orElseGet(() -> campaignProgressRepository.save(CampaignProgress.builder()
                        .campaignId(campaign.getId())
                        .customerId(customer.getId())
                        .state("{}")
                        .status(CampaignProgressStatus.IN_PROGRESS)
                        .updatedAt(LocalDateTime.now())
                        .build()));

        ObjectNode state = (ObjectNode) configSupport.parseJson(progress.getState()).deepCopy();
        Long rewardId = null;
        int currentStamps = configSupport.currentStamps(state);
        int requiredStamps = configSupport.requiredQuantity(configSupport.parseJson(campaign.getConfig()));

        switch (request.getAction()) {
            case ADD_STAMPS -> {
                int quantity = request.getQuantity() != null ? Math.max(1, request.getQuantity()) : 1;
                configSupport.setStamps(state, currentStamps + quantity);
                progress.setState(configSupport.writeJsonObject(state));
                currentStamps = configSupport.currentStamps(state);
            }
            case GRANT_REWARD -> {
                CampaignReward reward = campaignRewardService.issueReward(
                        campaign, customer.getId(), progress.getId(), request.getOrderId(), null);
                rewardId = reward.getId();
                progress.setStatus(CampaignProgressStatus.REWARDED);
            }
            case LINK_ORDER -> {
                if (request.getOrderId() == null) {
                    throw new BadRequestException("Sipariş ID zorunludur");
                }
                campaignEvaluationService.linkOrderToCustomer(menuId, request.getOrderId(), customer.getId());
            }
            default -> throw new BadRequestException("Geçersiz işlem");
        }

        campaignProgressRepository.save(progress);
        campaignManualGrantRepository.save(CampaignManualGrant.builder()
                .campaignId(campaign.getId())
                .menuId(menuId)
                .waiterId(waiter.getId())
                .customerId(customer.getId())
                .customerEmail(normalized)
                .action(request.getAction())
                .quantity(request.getQuantity())
                .orderId(request.getOrderId())
                .note(request.getNote().trim())
                .createdAt(LocalDateTime.now())
                .build());

        return CampaignDtos.ManualGrantResponse.builder()
                .message("Kampanya hakkı tanımlandı.")
                .currentStamps(currentStamps)
                .requiredStamps(requiredStamps)
                .rewardId(rewardId)
                .build();
    }

    private MenuWaiter requireWaiterForMenu(Long menuId) {
        return waiterAccessService.requireWaiterForMenu(menuId);
    }

    private void ensureMembership(Long customerId, Long menuId, Long businessId) {
        CustomerMembership membership = customerMembershipRepository
                .findByCustomerIdAndMenuId(customerId, menuId)
                .orElseGet(() -> customerMembershipRepository.save(CustomerMembership.builder()
                        .customerId(customerId)
                        .menuId(menuId)
                        .businessId(businessId)
                        .status(MembershipStatus.ACTIVE)
                        .joinedAt(LocalDateTime.now())
                        .build()));
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            membership.setStatus(MembershipStatus.ACTIVE);
            customerMembershipRepository.save(membership);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("E-posta zorunludur");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
