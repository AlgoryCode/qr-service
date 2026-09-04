package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsConnectionService;
import com.ael.algoryqrservice.messaging.IntegrationMessagePublisher;
import com.ael.algoryqrservice.model.IntegrationJob;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.dto.IntegrationPendingProductDtos;
import com.ael.algoryqrservice.model.enums.IntegrationDirection;
import com.ael.algoryqrservice.model.enums.IntegrationJobStatus;
import com.ael.algoryqrservice.repository.IntegrationJobRepository;
import com.ael.algoryqrservice.repository.MenuCategoryRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IntegrationExportService {

    private static final String PROVIDER_UBEREATS = "UBEREATS";

    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuSubCategoryRepository menuSubCategoryRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final IntegrationJobRepository jobRepository;
    private final IntegrationMessagePublisher messagePublisher;
    private final UberEatsConnectionService uberEatsConnectionService;
    private final UberEatsClient uberEatsClient;
    private final UberEatsPayloadMapper payloadMapper;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    @Transactional
    public IntegrationPendingProductDtos.JobAccepted exportToUberEats(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        UberEatsConnection connection = uberEatsConnectionService.requireConnected();
        ObjectNode snapshot = buildInternalSnapshot(menu);
        if (snapshot.withArray("products").isEmpty()) {
            throw new BadRequestException("Menüde aktarılacak ürün yok");
        }
        return enqueue(menu, IntegrationDirection.EXPORT_TO_UBEREATS, snapshot, connection.getRestaurantId());
    }

    @Transactional
    public IntegrationPendingProductDtos.JobAccepted importFromUberEats(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        UberEatsConnection connection = uberEatsConnectionService.requireConnected();
        UberEatsDtos.Credentials credentials = uberEatsConnectionService.decrypt(connection);
        JsonNode partnerMenu = uberEatsClient.getMenu(credentials);
        ObjectNode snapshot = buildPartnerSnapshot(menu, partnerMenu);
        if (snapshot.withArray("products").isEmpty()) {
            throw new BadRequestException("Uber Eats menüsünde aktarılacak ürün yok");
        }
        return enqueue(menu, IntegrationDirection.IMPORT_FROM_UBEREATS, snapshot, connection.getRestaurantId());
    }

    @Transactional(readOnly = true)
    public ObjectNode getSnapshot(UUID jobId) {
        return (ObjectNode) requireJob(jobId).getSnapshot();
    }

    @Transactional(readOnly = true)
    public IntegrationJob requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration job bulunamadı"));
    }

    @Transactional(readOnly = true)
    public List<IntegrationJob> listByStatuses(Collection<String> statuses) {
        return jobRepository.findByStatusInOrderByCreatedAtAsc(statuses);
    }

    @Transactional
    public IntegrationJob updateJob(UUID jobId, IntegrationPendingProductDtos.JobUpdateRequest request) {
        IntegrationJob job = requireJob(jobId);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            if (IntegrationJobStatus.AI_PROCESSING.equals(request.getStatus())
                    && !IntegrationJobStatus.QUEUED.equals(job.getStatus())
                    && !IntegrationJobStatus.AI_PROCESSING.equals(job.getStatus())) {
                return job;
            }
            job.setStatus(request.getStatus().trim());
            if (job.getStartedAt() == null
                    && (IntegrationJobStatus.AI_PROCESSING.equals(job.getStatus())
                    || IntegrationJobStatus.BATCH_SUBMITTED.equals(job.getStatus()))) {
                job.setStartedAt(LocalDateTime.now());
            }
            if (IntegrationJobStatus.WAITING_APPROVAL.equals(job.getStatus())
                    || IntegrationJobStatus.FAILED.equals(job.getStatus())
                    || IntegrationJobStatus.BATCH_COMPLETED.equals(job.getStatus())) {
                job.setFinishedAt(LocalDateTime.now());
            }
        }
        if (request.getAiBatchId() != null) {
            job.setAiBatchId(request.getAiBatchId());
        }
        if (request.getAiInputFileId() != null) {
            job.setAiInputFileId(request.getAiInputFileId());
        }
        if (request.getAiOutputFileId() != null) {
            job.setAiOutputFileId(request.getAiOutputFileId());
        }
        if (request.getErrorMessage() != null) {
            job.setErrorMessage(request.getErrorMessage());
        }
        return jobRepository.save(job);
    }

    private IntegrationPendingProductDtos.JobAccepted enqueue(
            Menu menu,
            String direction,
            ObjectNode snapshot,
            String externalStoreId
    ) {
        LocalDateTime now = LocalDateTime.now();
        IntegrationJob job = IntegrationJob.builder()
                .id(UUID.randomUUID())
                .tenantId(menu.getUserId())
                .menuId(menu.getMenuId())
                .provider(PROVIDER_UBEREATS)
                .direction(direction)
                .status(IntegrationJobStatus.QUEUED)
                .snapshotVersion(1)
                .snapshot(snapshot)
                .externalStoreId(externalStoreId)
                .createdAt(now)
                .build();
        IntegrationJob saved = jobRepository.save(job);
        messagePublisher.publishAiRequested(saved);
        return IntegrationPendingProductDtos.JobAccepted.builder()
                .jobId(saved.getId())
                .status(saved.getStatus())
                .direction(saved.getDirection())
                .build();
    }

    private ObjectNode buildInternalSnapshot(Menu menu) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("menuId", menu.getMenuId());
        root.put("tenantId", menu.getUserId());
        root.put("businessName", menu.getBusinessName());
        ArrayNode productNodes = root.putArray("products");

        List<MenuProduct> products = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menu.getMenuId());
        Map<Long, MenuSubCategory> subCategories = menuSubCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menu.getMenuId())
                .stream()
                .collect(Collectors.toMap(MenuSubCategory::getId, Function.identity(), (a, b) -> a, HashMap::new));
        Map<Long, MenuCategory> categories = menuCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menu.getMenuId())
                .stream()
                .collect(Collectors.toMap(MenuCategory::getId, Function.identity(), (a, b) -> a, HashMap::new));

        for (MenuProduct product : products) {
            if (product.getProductId() == null) {
                continue;
            }
            ObjectNode node = productNodes.addObject();
            String sourceProductId = String.valueOf(product.getProductId());
            node.put("sourceProductId", sourceProductId);
            node.put("internalProductId", product.getProductId());
            node.put("name", product.getName());
            if (product.getDescription() != null) {
                node.put("description", product.getDescription());
            }
            if (product.getPrice() != null) {
                node.put("price", product.getPrice());
            } else {
                node.putNull("price");
            }
            node.put("currency", product.getCurrency() == null ? "TRY" : product.getCurrency());
            node.put("available", product.isAvailable());
            if (product.getImageUrl() != null) {
                node.put("imageUrl", product.getImageUrl());
            }
            MenuSubCategory sub = subCategories.get(product.getSubCategoryId());
            if (sub != null) {
                node.put("subCategoryId", sub.getId());
                node.put("subcategory", sub.getName());
                MenuCategory category = categories.get(sub.getMenuCategoryId());
                if (category != null) {
                    node.put("category", category.getName());
                } else {
                    node.put("category", sub.getName());
                }
            }
            node.putArray("modifierGroupIds");
        }
        return root;
    }

    private ObjectNode buildPartnerSnapshot(Menu menu, JsonNode partnerMenu) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("menuId", menu.getMenuId());
        root.put("tenantId", menu.getUserId());
        root.put("businessName", menu.getBusinessName());
        ArrayNode productNodes = root.putArray("products");
        for (UberEatsDtos.ProductResponse product : payloadMapper.toProducts(partnerMenu)) {
            if (product.getId() == null || product.getId().isBlank()) {
                continue;
            }
            ObjectNode node = productNodes.addObject();
            node.put("sourceProductId", product.getId());
            node.put("name", product.getName());
            node.put("description", product.getDescription());
            if (product.getPrice() != null) {
                node.put("price", product.getPrice());
            } else {
                node.putNull("price");
            }
            node.put("currency", product.getCurrency() == null ? "TRY" : product.getCurrency());
            node.put("available", product.isAvailable());
            if (product.getImageUrl() != null) {
                node.put("imageUrl", product.getImageUrl());
            }
            if (product.getCategoryName() != null && !product.getCategoryName().isBlank()) {
                node.put("category", product.getCategoryName());
                node.put("subcategory", product.getCategoryName());
            }
            node.putArray("modifierGroupIds");
        }
        return root;
    }

    private Menu requireOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        Long currentUserId = securityUtils.getCurrentUserId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }
}
