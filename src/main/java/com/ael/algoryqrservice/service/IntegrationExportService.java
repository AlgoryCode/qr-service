package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuSubCategoryRepository menuSubCategoryRepository;
    private final IntegrationJobRepository jobRepository;
    private final IntegrationMessagePublisher messagePublisher;
    private final UberEatsConnectionService uberEatsConnectionService;
    private final UberEatsClient uberEatsClient;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    @Transactional
    public IntegrationPendingProductDtos.JobAccepted exportToUberEats(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        uberEatsConnectionService.requireConnected(menuId);
        ObjectNode snapshot = buildInternalSnapshot(menu);
        if (snapshot.withArray("products").isEmpty()) {
            throw new BadRequestException("Menüde aktarılacak ürün yok");
        }
        return enqueue(menu, IntegrationDirection.EXPORT_TO_UBEREATS, snapshot, null);
    }

    @Transactional
    public IntegrationPendingProductDtos.JobAccepted importFromUberEats(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        UberEatsConnection connection = uberEatsConnectionService.requireConnected(menuId);
        UberEatsDtos.Credentials credentials = uberEatsConnectionService.decrypt(connection);
        JsonNode uberMenu = uberEatsClient.getMenu(credentials);
        ObjectNode snapshot = buildUberSnapshot(menu, uberMenu);
        if (snapshot.withArray("products").isEmpty()) {
            throw new BadRequestException("Uber Eats menüsünde aktarılacak ürün yok");
        }
        return enqueue(menu, IntegrationDirection.IMPORT_FROM_UBEREATS, snapshot, connection.getStoreId());
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
        List<MenuProduct> products = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menu.getMenuId());
        Map<Long, MenuSubCategory> subMap = menuSubCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menu.getMenuId())
                .stream()
                .collect(Collectors.toMap(MenuSubCategory::getId, Function.identity()));
        Map<Long, MenuCategory> categoryMap = menuCategoryRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menu.getMenuId())
                .stream()
                .collect(Collectors.toMap(MenuCategory::getId, Function.identity()));

        ObjectNode root = objectMapper.createObjectNode();
        root.put("menuId", menu.getMenuId());
        root.put("tenantId", menu.getUserId());
        root.put("businessName", menu.getBusinessName());
        ArrayNode productNodes = root.putArray("products");
        for (MenuProduct product : products) {
            MenuSubCategory sub = subMap.get(product.getSubCategoryId());
            MenuCategory category = sub == null ? null : categoryMap.get(sub.getMenuCategoryId());
            ObjectNode node = productNodes.addObject();
            node.put("internalProductId", product.getProductId());
            node.put("sourceProductId", String.valueOf(product.getProductId()));
            node.put("name", product.getName());
            node.put("description", product.getDescription());
            if (product.getPrice() != null) {
                node.put("price", product.getPrice());
            } else {
                node.putNull("price");
            }
            node.put("currency", product.getCurrency());
            node.put("available", product.isAvailable());
            node.put("imageUrl", product.getImageUrl());
            node.put("subCategoryId", product.getSubCategoryId());
            if (sub != null) {
                node.put("subcategory", sub.getName());
            }
            if (category != null) {
                node.put("categoryId", category.getId());
                node.put("category", category.getName());
            }
            node.putArray("modifierGroupIds");
        }
        return root;
    }

    private ObjectNode buildUberSnapshot(Menu menu, JsonNode uberMenu) {
        Map<String, String> categoryNames = new HashMap<>();
        Map<String, String> itemCategory = new HashMap<>();
        if (uberMenu != null && uberMenu.has("categories") && uberMenu.get("categories").isArray()) {
            for (JsonNode category : uberMenu.get("categories")) {
                String categoryId = category.path("id").asText(null);
                String categoryName = multiLangText(category.get("title"));
                if (categoryId != null) {
                    categoryNames.put(categoryId, categoryName);
                }
                JsonNode entities = category.get("entities");
                if (entities != null && entities.isArray()) {
                    for (JsonNode entity : entities) {
                        if ("ITEM".equalsIgnoreCase(entity.path("type").asText())
                                && entity.hasNonNull("id")) {
                            itemCategory.put(entity.get("id").asText(), categoryName);
                        }
                    }
                }
            }
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("menuId", menu.getMenuId());
        root.put("tenantId", menu.getUserId());
        root.put("businessName", menu.getBusinessName());
        ArrayNode productNodes = root.putArray("products");
        JsonNode items = uberMenu == null ? null : uberMenu.get("items");
        if (items == null || !items.isArray()) {
            return root;
        }
        for (JsonNode item : items) {
            String itemId = item.path("id").asText(null);
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ObjectNode node = productNodes.addObject();
            node.put("sourceProductId", itemId);
            node.put("name", multiLangText(item.get("title")));
            node.put("description", multiLangText(item.get("description")));
            node.put("price", fromMinorUnits(item.path("price_info").path("price").asInt(0)));
            node.put("currency", "TRY");
            node.put("available", !item.has("suspension_info")
                    || item.path("suspension_info").path("suspension").isNull()
                    || item.path("suspension_info").path("suspension").isMissingNode());
            String imageUrl = firstImage(item.get("image_url"));
            if (imageUrl != null) {
                node.put("imageUrl", imageUrl);
            }
            String category = itemCategory.get(itemId);
            if (category != null) {
                node.put("category", category);
                node.put("subcategory", category);
            } else if (!categoryNames.isEmpty()) {
                Iterator<String> values = categoryNames.values().iterator();
                if (values.hasNext()) {
                    String fallback = values.next();
                    node.put("category", fallback);
                    node.put("subcategory", fallback);
                }
            }
            node.putArray("modifierGroupIds");
        }
        return root;
    }

    private String multiLangText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        JsonNode translations = node.get("translations");
        if (translations != null && translations.isObject()) {
            if (translations.hasNonNull("en_us")) {
                return translations.get("en_us").asText();
            }
            var fields = translations.fields();
            if (fields.hasNext()) {
                return fields.next().getValue().asText(null);
            }
        }
        if (translations != null && translations.isArray() && !translations.isEmpty()) {
            return translations.get(0).path("value").asText(null);
        }
        if (node.hasNonNull("en_us")) {
            return node.get("en_us").asText();
        }
        return null;
    }

    private String firstImage(JsonNode imageUrl) {
        if (imageUrl == null || imageUrl.isNull() || imageUrl.isMissingNode()) {
            return null;
        }
        if (imageUrl.isTextual()) {
            return imageUrl.asText();
        }
        if (imageUrl.isArray() && !imageUrl.isEmpty()) {
            return imageUrl.get(0).asText(null);
        }
        return null;
    }

    private BigDecimal fromMinorUnits(int minor) {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
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
