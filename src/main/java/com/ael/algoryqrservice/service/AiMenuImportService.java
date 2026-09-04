package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.messaging.AiMenuImportMessagePublisher;
import com.ael.algoryqrservice.model.AiMenuImportDraft;
import com.ael.algoryqrservice.model.AiMenuImportJob;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.enums.AiMenuImportJobStatus;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.repository.AiMenuImportDraftRepository;
import com.ael.algoryqrservice.repository.AiMenuImportJobRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiMenuImportService {

    private final AiMenuImportJobRepository jobRepository;
    private final AiMenuImportDraftRepository draftRepository;
    private final MenuRepository menuRepository;
    private final MenuService menuService;
    private final MenuCategoryService menuCategoryService;
    private final AiMenuImportMessagePublisher messagePublisher;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiMenuImportDtos.JobAccepted createJob(Long menuId, AiMenuImportDtos.CreateJobRequest request) {
        Menu menu = requireOwnedMenuEntity(menuId);
        List<String> imageUrls = normalizeImageUrls(request.getImageUrls());
        if (imageUrls.isEmpty()) {
            throw new BadRequestException("En az bir görsel URL zorunludur");
        }
        LocalDateTime now = LocalDateTime.now();
        ArrayNode urlsNode = objectMapper.createArrayNode();
        imageUrls.forEach(urlsNode::add);
        AiMenuImportJob job = AiMenuImportJob.builder()
                .id(UUID.randomUUID())
                .tenantId(menu.getUserId())
                .menuId(menuId)
                .status(AiMenuImportJobStatus.QUEUED)
                .imageUrls(urlsNode)
                .createdAt(now)
                .build();
        AiMenuImportJob saved = jobRepository.save(job);
        messagePublisher.publishAiRequested(saved);
        return AiMenuImportDtos.JobAccepted.builder()
                .jobId(saved.getId())
                .status(saved.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public AiMenuImportDtos.JobResponse getJob(Long menuId, UUID jobId) {
        menuService.requireOwnedMenu(menuId);
        return toJobResponse(requireOwnedJob(menuId, jobId));
    }

    @Transactional(readOnly = true)
    public Page<AiMenuImportDtos.DraftResponse> listDrafts(
            Long menuId,
            String status,
            UUID jobId,
            Pageable pageable
    ) {
        menuService.requireOwnedMenu(menuId);
        String approvalStatus = status == null || status.isBlank()
                ? AiMenuImportJobStatus.WAITING_APPROVAL
                : status.trim();
        Page<AiMenuImportDraft> page = jobId == null
                ? draftRepository.findByMenuIdAndApprovalStatusOrderByCreatedAtAsc(menuId, approvalStatus, pageable)
                : draftRepository.findByMenuIdAndJobIdAndApprovalStatusOrderByCreatedAtAsc(
                        menuId, jobId, approvalStatus, pageable
                );
        return page.map(this::toDraftResponse);
    }

    @Transactional
    public AiMenuImportDtos.DraftResponse updateDraft(
            Long menuId,
            UUID draftId,
            AiMenuImportDtos.DraftUpdateRequest request
    ) {
        AiMenuImportDraft draft = requireWaitingDraft(menuId, draftId);
        ObjectNode data = asObjectNode(draft.getProductData());
        putIfPresent(data, "name", request.getName());
        putIfPresent(data, "description", request.getDescription());
        putIfPresent(data, "currency", request.getCurrency());
        putIfPresent(data, "category", request.getCategory());
        putIfPresent(data, "subcategory", request.getSubcategory());
        putIfPresent(data, "imageUrl", request.getImageUrl());
        if (request.getPrice() != null) {
            data.put("price", request.getPrice());
        }
        if (request.getSubCategoryId() != null) {
            data.put("subCategoryId", request.getSubCategoryId());
        }
        if (request.getAvailable() != null) {
            data.put("available", request.getAvailable());
        }
        if (request.getNutrition() != null) {
            data.set("nutrition", objectMapper.valueToTree(request.getNutrition()));
        }
        draft.setProductData(data);
        draft.setUpdatedAt(LocalDateTime.now());
        return toDraftResponse(draftRepository.save(draft));
    }

    @Transactional
    public AiMenuImportDtos.DraftResponse approve(Long menuId, UUID draftId, Long userId) {
        AiMenuImportDraft draft = requireWaitingDraft(menuId, draftId);
        MenuDtos.MenuProductResponse created = createProductFromDraft(menuId, draft);
        draft.setApprovalStatus(AiMenuImportJobStatus.APPROVED);
        draft.setApprovedBy(userId);
        draft.setApprovedAt(LocalDateTime.now());
        draft.setPublishedProductId(created.getProductId());
        draft.setUpdatedAt(LocalDateTime.now());
        return toDraftResponse(draftRepository.save(draft));
    }

    @Transactional
    public List<AiMenuImportDtos.DraftResponse> bulkApprove(Long menuId, List<UUID> draftIds, Long userId) {
        menuService.requireOwnedMenu(menuId);
        List<AiMenuImportDraft> drafts = draftRepository.findByMenuIdAndIdIn(menuId, draftIds);
        if (drafts.size() != draftIds.size()) {
            throw new BadRequestException("Bazı taslaklar bulunamadı");
        }
        LocalDateTime now = LocalDateTime.now();
        List<AiMenuImportDtos.DraftResponse> responses = new ArrayList<>();
        for (AiMenuImportDraft draft : drafts) {
            if (!AiMenuImportJobStatus.WAITING_APPROVAL.equals(draft.getApprovalStatus())) {
                throw new BadRequestException("Yalnızca onay bekleyen taslaklar onaylanabilir");
            }
            MenuDtos.MenuProductResponse created = createProductFromDraft(menuId, draft);
            draft.setApprovalStatus(AiMenuImportJobStatus.APPROVED);
            draft.setApprovedBy(userId);
            draft.setApprovedAt(now);
            draft.setPublishedProductId(created.getProductId());
            draft.setUpdatedAt(now);
            responses.add(toDraftResponse(draftRepository.save(draft)));
        }
        return responses;
    }

    @Transactional
    public void reject(Long menuId, UUID draftId, AiMenuImportDtos.RejectRequest request) {
        AiMenuImportDraft draft = requireWaitingDraft(menuId, draftId);
        draft.setApprovalStatus(AiMenuImportJobStatus.REJECTED);
        draft.setRejectReason(request == null ? null : blankToNull(request.getReason()));
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public AiMenuImportJob requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI menu import job bulunamadı"));
    }

    @Transactional(readOnly = true)
    public List<AiMenuImportJob> listByStatuses(Collection<String> statuses) {
        return jobRepository.findByStatusInOrderByCreatedAtAsc(statuses);
    }

    @Transactional
    public AiMenuImportJob updateJob(UUID jobId, AiMenuImportDtos.JobUpdateRequest request) {
        AiMenuImportJob job = requireJob(jobId);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim();
            if (AiMenuImportJobStatus.EXTRACTING.equals(status)
                    && !AiMenuImportJobStatus.QUEUED.equals(job.getStatus())
                    && !AiMenuImportJobStatus.EXTRACTING.equals(job.getStatus())) {
                return job;
            }
            if (AiMenuImportJobStatus.BATCH_SUBMITTED.equals(status)
                    && (AiMenuImportJobStatus.EXTRACTING.equals(job.getStatus())
                    || AiMenuImportJobStatus.BATCH_SUBMITTED.equals(job.getStatus()))) {
                job.setStatus(status);
            } else if (!AiMenuImportJobStatus.WAITING_APPROVAL.equals(job.getStatus())
                    && !AiMenuImportJobStatus.FAILED.equals(job.getStatus())) {
                job.setStatus(status);
            }
            if (AiMenuImportJobStatus.EXTRACTING.equals(status) && job.getStartedAt() == null) {
                job.setStartedAt(LocalDateTime.now());
            }
            if (AiMenuImportJobStatus.WAITING_APPROVAL.equals(status)
                    || AiMenuImportJobStatus.FAILED.equals(status)) {
                job.setFinishedAt(LocalDateTime.now());
            }
        }
        if (request.getAiBatchId() != null) {
            job.setAiBatchId(blankToNull(request.getAiBatchId()));
        }
        if (request.getAiInputFileId() != null) {
            job.setAiInputFileId(blankToNull(request.getAiInputFileId()));
        }
        if (request.getAiOutputFileId() != null) {
            job.setAiOutputFileId(blankToNull(request.getAiOutputFileId()));
        }
        if (request.getErrorMessage() != null) {
            job.setErrorMessage(blankToNull(request.getErrorMessage()));
        }
        if (request.getExtractedProducts() != null) {
            job.setExtractedProducts(request.getExtractedProducts());
        }
        return jobRepository.save(job);
    }

    private MenuDtos.MenuProductResponse createProductFromDraft(Long menuId, AiMenuImportDraft draft) {
        JsonNode data = draft.getProductData();
        String name = text(data, "name");
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Ürün adı zorunludur");
        }
        BigDecimal price = decimal(data, "price");
        if (price == null) {
            throw new BadRequestException("Fiyat zorunludur");
        }
        Long subCategoryId = resolveSubCategoryId(menuId, data);
        NutritionFacts nutrition = null;
        if (data != null && data.has("nutrition") && !data.get("nutrition").isNull()) {
            nutrition = objectMapper.convertValue(data.get("nutrition"), NutritionFacts.class);
        }
        MenuDtos.MenuProductRequest request = MenuDtos.MenuProductRequest.builder()
                .name(name.trim())
                .description(text(data, "description"))
                .price(price)
                .currency(text(data, "currency") == null ? "TRY" : text(data, "currency"))
                .subCategoryId(subCategoryId)
                .imageUrl(text(data, "imageUrl"))
                .available(data == null || !data.has("available") || data.get("available").asBoolean(true))
                .servesPeopleMin(1)
                .servesPeopleMax(1)
                .nutrition(nutrition)
                .build();
        return menuService.createProduct(menuId, request);
    }

    private Long resolveSubCategoryId(Long menuId, JsonNode data) {
        if (data != null && data.hasNonNull("subCategoryId")) {
            long id = data.get("subCategoryId").asLong();
            menuCategoryService.requireSubCategory(menuId, id);
            return id;
        }
        String subcategory = text(data, "subcategory");
        String category = text(data, "category");
        Map<Long, MenuSubCategory> subs = menuCategoryService.loadSubCategoryMap(menuId);
        Map<Long, MenuCategory> mains = menuCategoryService.loadCategoryMap(menuId);
        if (subcategory != null && !subcategory.isBlank()) {
            String needle = normalizeName(subcategory);
            for (MenuSubCategory sub : subs.values()) {
                if (normalizeName(sub.getName()).equals(needle)) {
                    return sub.getId();
                }
            }
        }
        if (category != null && !category.isBlank()) {
            String needle = normalizeName(category);
            for (MenuSubCategory sub : subs.values()) {
                MenuCategory main = mains.get(sub.getMenuCategoryId());
                if (main != null && normalizeName(main.getName()).equals(needle)) {
                    return sub.getId();
                }
            }
        }
        throw new BadRequestException("Alt kategori seçilmelidir (subCategoryId)");
    }

    private Menu requireOwnedMenuEntity(Long menuId) {
        Long userId = securityUtils.getCurrentUserId();
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!userId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private AiMenuImportJob requireOwnedJob(Long menuId, UUID jobId) {
        return jobRepository.findByIdAndMenuId(jobId, menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI menu import job bulunamadı"));
    }

    private AiMenuImportDraft requireWaitingDraft(Long menuId, UUID draftId) {
        menuService.requireOwnedMenu(menuId);
        AiMenuImportDraft draft = draftRepository.findByIdAndMenuId(draftId, menuId)
                .orElseThrow(() -> new BadRequestException("Taslak bulunamadı"));
        if (!AiMenuImportJobStatus.WAITING_APPROVAL.equals(draft.getApprovalStatus())) {
            throw new BadRequestException("Taslak onay bekleyen durumda değil");
        }
        return draft;
    }

    private List<String> normalizeImageUrls(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();
    }

    private AiMenuImportDtos.JobResponse toJobResponse(AiMenuImportJob job) {
        List<String> urls = new ArrayList<>();
        if (job.getImageUrls() != null && job.getImageUrls().isArray()) {
            for (JsonNode node : job.getImageUrls()) {
                if (node != null && !node.isNull()) {
                    urls.add(node.asText());
                }
            }
        }
        return AiMenuImportDtos.JobResponse.builder()
                .jobId(job.getId())
                .menuId(job.getMenuId())
                .status(job.getStatus())
                .imageUrls(urls)
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .build();
    }

    private AiMenuImportDtos.DraftResponse toDraftResponse(AiMenuImportDraft draft) {
        return AiMenuImportDtos.DraftResponse.builder()
                .id(draft.getId())
                .jobId(draft.getJobId())
                .menuId(draft.getMenuId())
                .sourceProductId(draft.getSourceProductId())
                .productData(draft.getProductData())
                .confidence(draft.getConfidence())
                .approvalStatus(draft.getApprovalStatus())
                .publishedProductId(draft.getPublishedProductId())
                .rejectReason(draft.getRejectReason())
                .errorMessage(draft.getErrorMessage())
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    private ObjectNode asObjectNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return objectMapper.createObjectNode();
        }
        return (ObjectNode) node.deepCopy();
    }

    private void putIfPresent(ObjectNode data, String field, String value) {
        if (value != null) {
            data.put(field, value);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        String raw = value.asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", ".").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
