package com.ael.algoryqrservice.service.campaign;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Campaign;
import com.ael.algoryqrservice.model.CampaignTemplate;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.CampaignDtos;
import com.ael.algoryqrservice.model.enums.CampaignStatus;
import com.ael.algoryqrservice.repository.CampaignRepository;
import com.ael.algoryqrservice.repository.CampaignRewardRepository;
import com.ael.algoryqrservice.repository.CampaignTemplateRepository;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignRewardRepository campaignRewardRepository;
    private final CampaignTemplateRepository campaignTemplateRepository;
    private final CustomerRepository customerRepository;
    private final MenuRepository menuRepository;
    private final CampaignConfigSupport configSupport;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<CampaignDtos.TemplateResponse> listTemplates() {
        return campaignTemplateRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignDtos.CampaignResponse> listCampaigns(Long menuId) {
        requireOwnedMenu(menuId);
        return campaignRepository.findByMenuIdOrderByCreatedAtDesc(menuId).stream()
                .map(this::toCampaignResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignDtos.ActiveCampaignResponse> listActiveCampaigns(Long menuId) {
        LocalDateTime now = LocalDateTime.now();
        return campaignRepository
                .findByMenuIdAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                        menuId, CampaignStatus.ACTIVE, now, now)
                .stream()
                .map(this::toActiveCampaignResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> activeCampaignProductIds(Long menuId) {
        Set<Long> productIds = new HashSet<>();
        for (CampaignDtos.ActiveCampaignResponse campaign : listActiveCampaigns(menuId)) {
            if (campaign.getTargetProductIds() != null) {
                productIds.addAll(campaign.getTargetProductIds());
            }
        }
        return productIds;
    }

    @Transactional(readOnly = true)
    public CampaignDtos.CampaignResponse getCampaign(Long menuId, Long campaignId) {
        requireOwnedMenu(menuId);
        return toCampaignResponse(requireCampaign(menuId, campaignId));
    }

    @Transactional(readOnly = true)
    public CampaignDtos.WinnerPageResponse listWinners(
            Long menuId,
            Long campaignId,
            String query,
            int page,
            int size
    ) {
        requireOwnedMenu(menuId);
        requireCampaign(menuId, campaignId);
        String trimmedQuery = query != null && !query.isBlank() ? query.trim() : null;
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "issuedAt")
        );
        Page<com.ael.algoryqrservice.model.CampaignReward> result = campaignRewardRepository.searchWinners(
                campaignId,
                trimmedQuery,
                pageable
        );
        List<Long> customerIds = result.getContent().stream()
                .map(com.ael.algoryqrservice.model.CampaignReward::getCustomerId)
                .distinct()
                .toList();
        Map<Long, Customer> customers = customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        List<CampaignDtos.WinnerResponse> content = result.getContent().stream()
                .map(reward -> toWinnerResponse(reward, customers.get(reward.getCustomerId())))
                .toList();
        return CampaignDtos.WinnerPageResponse.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }

    @Transactional
    public CampaignDtos.CampaignResponse createCampaign(Long menuId, CampaignDtos.CreateCampaignRequest request) {
        Menu menu = requireOwnedMenu(menuId);
        CampaignTemplate template = campaignTemplateRepository.findByCode(request.getTemplateCode())
                .orElseThrow(() -> new BadRequestException("Geçersiz kampanya şablonu"));
        validateDates(request.getStartsAt(), request.getEndsAt());
        LocalDateTime now = LocalDateTime.now();
        Campaign saved = campaignRepository.save(Campaign.builder()
                .menuId(menuId)
                .businessId(menu.getUserId())
                .templateCode(template.getCode())
                .name(request.getName().trim())
                .slogan(trim(request.getSlogan()))
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .status(CampaignStatus.DRAFT)
                .config(configSupport.writeJsonMap(request.getConfig()))
                .createdAt(now)
                .updatedAt(now)
                .build());
        return toCampaignResponse(saved);
    }

    @Transactional
    public CampaignDtos.CampaignResponse updateCampaign(
            Long menuId,
            Long campaignId,
            CampaignDtos.UpdateCampaignRequest request
    ) {
        Campaign campaign = requireCampaign(menuId, campaignId);
        if (request.getName() != null && !request.getName().isBlank()) {
            campaign.setName(request.getName().trim());
        }
        if (request.getSlogan() != null) {
            campaign.setSlogan(trim(request.getSlogan()));
        }
        if (request.getStartsAt() != null) {
            campaign.setStartsAt(request.getStartsAt());
        }
        if (request.getEndsAt() != null) {
            campaign.setEndsAt(request.getEndsAt());
        }
        validateDates(campaign.getStartsAt(), campaign.getEndsAt());
        if (request.getConfig() != null) {
            campaign.setConfig(configSupport.writeJsonMap(request.getConfig()));
        }
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDtos.CampaignResponse activateCampaign(Long menuId, Long campaignId) {
        Campaign campaign = requireCampaign(menuId, campaignId);
        campaign.setStatus(CampaignStatus.ACTIVE);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDtos.CampaignResponse pauseCampaign(Long menuId, Long campaignId) {
        Campaign campaign = requireCampaign(menuId, campaignId);
        campaign.setStatus(CampaignStatus.PAUSED);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional(readOnly = true)
    public Campaign requireActiveCampaign(Long menuId, Long campaignId) {
        Campaign campaign = requireCampaign(menuId, campaignId);
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new BadRequestException("Kampanya aktif değil");
        }
        LocalDateTime now = LocalDateTime.now();
        if (campaign.getStartsAt().isAfter(now) || campaign.getEndsAt().isBefore(now)) {
            throw new BadRequestException("Kampanya tarih aralığı dışında");
        }
        return campaign;
    }

    @Transactional(readOnly = true)
    public Campaign requireCampaign(Long menuId, Long campaignId) {
        return campaignRepository.findByIdAndMenuId(campaignId, menuId)
                .orElseThrow(() -> new NotFoundException("Kampanya bulunamadı"));
    }

    private Menu requireOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        if (!menu.getUserId().equals(securityUtils.getCurrentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private void validateDates(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new BadRequestException("Bitiş tarihi başlangıçtan sonra olmalıdır");
        }
    }

    private CampaignDtos.TemplateResponse toTemplateResponse(CampaignTemplate template) {
        return CampaignDtos.TemplateResponse.builder()
                .code(template.getCode())
                .name(template.getName())
                .description(template.getDescription())
                .icon(template.getIcon())
                .configSchema(configSupport.parseJsonMap(template.getConfigSchema()))
                .sortOrder(template.getSortOrder())
                .build();
    }

    private CampaignDtos.CampaignResponse toCampaignResponse(Campaign campaign) {
        return CampaignDtos.CampaignResponse.builder()
                .id(campaign.getId())
                .menuId(campaign.getMenuId())
                .templateCode(campaign.getTemplateCode())
                .name(campaign.getName())
                .slogan(campaign.getSlogan())
                .startsAt(campaign.getStartsAt())
                .endsAt(campaign.getEndsAt())
                .status(campaign.getStatus())
                .config(configSupport.parseJsonMap(campaign.getConfig()))
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    private CampaignDtos.ActiveCampaignResponse toActiveCampaignResponse(Campaign campaign) {
        JsonNode config = configSupport.parseJson(campaign.getConfig());
        List<Long> targetProductIds = new ArrayList<>();
        if ("STAMP_CARD".equals(campaign.getTemplateCode())) {
            targetProductIds = configSupport.targetProductIds(config);
        }
        return CampaignDtos.ActiveCampaignResponse.builder()
                .id(campaign.getId())
                .templateCode(campaign.getTemplateCode())
                .name(campaign.getName())
                .slogan(campaign.getSlogan())
                .config(configSupport.parseJsonMap(campaign.getConfig()))
                .targetProductIds(targetProductIds)
                .build();
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CampaignDtos.WinnerResponse toWinnerResponse(
            com.ael.algoryqrservice.model.CampaignReward reward,
            Customer customer
    ) {
        return CampaignDtos.WinnerResponse.builder()
                .rewardId(reward.getId())
                .customerId(reward.getCustomerId())
                .firstName(customer != null ? customer.getFirstName() : null)
                .lastName(customer != null ? customer.getLastName() : null)
                .email(customer != null ? customer.getEmail() : null)
                .status(reward.getStatus())
                .issuedAt(reward.getIssuedAt())
                .redeemedAt(reward.getRedeemedAt())
                .orderId(reward.getOrderId())
                .build();
    }
}
