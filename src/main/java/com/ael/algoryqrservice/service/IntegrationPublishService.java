package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.integration.ubereatsmenu.UberEatsMenuPayloadMapper;
import com.ael.algoryqrservice.integration.ubereatsmenu.UberEatsMenuPublisher;
import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.enums.IntegrationJobStatus;
import com.ael.algoryqrservice.model.enums.IntegrationPublishTarget;
import com.ael.algoryqrservice.model.enums.NutritionBasis;
import com.ael.algoryqrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.repository.IntegrationPendingProductRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuSubCategoryRepository;
import com.ael.algoryqrservice.service.entitlement.FeatureUsageSyncRegistry;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.service.menuindex.MenuProductIndexNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationPublishService {

    private final IntegrationPendingProductRepository pendingProductRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuSubCategoryRepository menuSubCategoryRepository;
    private final UberEatsMenuPublisher uberEatsMenuPublisher;
    private final UberEatsMenuPayloadMapper uberEatsMenuPayloadMapper;
    private final EntitlementService entitlementService;
    private final FeatureUsageSyncRegistry usageSyncRegistry;
    private final MenuProductIndexNotifier menuProductIndexNotifier;

    @Transactional
    public void publish(UUID pendingProductId) {
        IntegrationPendingProduct product = pendingProductRepository.findById(pendingProductId)
                .orElseThrow(() -> new BadRequestException("Onaylanan ürün bulunamadı"));
        if (!IntegrationJobStatus.APPROVED.equals(product.getApprovalStatus())
                && !IntegrationJobStatus.PARTIALLY_PUBLISHED.equals(product.getApprovalStatus())
                && !IntegrationJobStatus.FAILED.equals(product.getApprovalStatus())) {
            return;
        }

        Set<String> targets = product.getPublishTargets() == null ? Set.of() : product.getPublishTargets();
        boolean internalOk = !targets.contains(IntegrationPublishTarget.INTERNAL_MENU)
                || product.getPublishedProductId() != null
                || publishInternal(product);
        boolean uberOk = !targets.contains(IntegrationPublishTarget.UBEREATS)
                || (product.getUberItemId() != null && !product.getUberItemId().isBlank())
                || publishUber(product);

        if (internalOk && uberOk) {
            product.setApprovalStatus(IntegrationJobStatus.PUBLISHED);
            product.setErrorMessage(null);
        } else if (internalOk || uberOk) {
            product.setApprovalStatus(IntegrationJobStatus.PARTIALLY_PUBLISHED);
        } else {
            product.setApprovalStatus(IntegrationJobStatus.FAILED);
        }
        product.setUpdatedAt(LocalDateTime.now());
        pendingProductRepository.save(product);
    }

    private boolean publishInternal(IntegrationPendingProduct pending) {
        try {
            JsonNode data = pending.getProductData();
            Long subCategoryId = resolveSubCategoryId(pending.getMenuId(), data);
            String name = requiredText(data, "name");
            BigDecimal price = data != null && data.hasNonNull("price")
                    ? data.get("price").decimalValue()
                    : BigDecimal.ZERO;
            String currency = textOrDefault(data, "currency", "TRY");
            String description = text(data, "description");
            String imageUrl = text(data, "imageUrl");
            boolean available = data == null || !data.has("available") || data.get("available").asBoolean(true);

            Long existingId = data != null && data.hasNonNull("internalProductId")
                    ? data.get("internalProductId").asLong()
                    : null;
            if (existingId != null) {
                Optional<MenuProduct> existing = menuProductRepository.findByProductIdAndDeletedFalse(existingId);
                if (existing.isPresent() && pending.getMenuId().equals(existing.get().getMenuId())) {
                    MenuProduct product = existing.get();
                    product.setName(name);
                    product.setDescription(trimToNull(description));
                    product.setPrice(price);
                    product.setCurrency(currency);
                    product.setSubCategoryId(subCategoryId);
                    product.setImageUrl(imageUrl);
                    product.setAvailable(available);
                    MenuProduct saved = menuProductRepository.save(product);
                    pending.setPublishedProductId(saved.getProductId());
                    menuProductIndexNotifier.productChanged(saved);
                    return true;
                }
            }

            entitlementService.assertMenuProductCreationAllowed(pending.getTenantId(), 1);
            int sortOrder = menuProductRepository
                    .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(pending.getMenuId())
                    .size();
            MenuProduct created = MenuProduct.builder()
                    .menuId(pending.getMenuId())
                    .name(name)
                    .description(trimToNull(description))
                    .price(price)
                    .currency(currency)
                    .subCategoryId(subCategoryId)
                    .tagIds(new HashSet<>())
                    .allergenIds(new HashSet<>())
                    .sortOrder(sortOrder)
                    .imageUrl(imageUrl)
                    .available(available)
                    .nutrition(defaultNutrition())
                    .build();
            MenuProduct saved = menuProductRepository.save(created);
            pending.setPublishedProductId(saved.getProductId());
            usageSyncRegistry.synchronize(pending.getTenantId(), CatalogProducts.MENU_PRODUCT);
            menuProductIndexNotifier.productChanged(saved);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Internal menu publish failed pendingProductId={}", pending.getId(), exception);
            pending.setErrorMessage(exception.getMessage());
            return false;
        }
    }

    private boolean publishUber(IntegrationPendingProduct pending) {
        try {
            JsonNode payload = uberEatsMenuPayloadMapper.toUberItem(pending);
            UberEatsMenuPublisher.PublishResult result = uberEatsMenuPublisher.publishItem(pending, payload);
            if (result.success()) {
                pending.setUberItemId(result.uberItemId());
                return true;
            }
            pending.setErrorMessage(result.errorMessage());
            if (result.retryable()) {
                throw new IllegalStateException(result.errorMessage());
            }
            return false;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Uber publish failed pendingProductId={}", pending.getId(), exception);
            pending.setErrorMessage(exception.getMessage());
            return false;
        }
    }

    private Long resolveSubCategoryId(Long menuId, JsonNode data) {
        if (data != null && data.hasNonNull("subCategoryId")) {
            Long id = data.get("subCategoryId").asLong();
            return menuSubCategoryRepository.findById(id)
                    .filter(sub -> menuId.equals(sub.getMenuId()) && !sub.isDeleted())
                    .map(MenuSubCategory::getId)
                    .orElseThrow(() -> new BadRequestException("Alt kategori bulunamadı"));
        }
        String subcategory = text(data, "subcategory");
        if (subcategory == null || subcategory.isBlank()) {
            throw new BadRequestException("Alt kategori zorunludur");
        }
        return menuSubCategoryRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscIdAsc(menuId)
                .stream()
                .filter(sub -> subcategory.equalsIgnoreCase(sub.getName()))
                .map(MenuSubCategory::getId)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Alt kategori bulunamadı: " + subcategory));
    }

    private NutritionFacts defaultNutrition() {
        return NutritionFacts.builder()
                .basis(NutritionBasis.PER_100G)
                .energyKj(BigDecimal.ZERO)
                .energyKcal(BigDecimal.ZERO)
                .fat(BigDecimal.ZERO)
                .carbohydrate(BigDecimal.ZERO)
                .fibre(BigDecimal.ZERO)
                .protein(BigDecimal.ZERO)
                .salt(BigDecimal.ZERO)
                .build();
    }

    private String requiredText(JsonNode data, String field) {
        String value = text(data, field);
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " zorunludur");
        }
        return value.trim();
    }

    private String text(JsonNode data, String field) {
        if (data == null || !data.hasNonNull(field)) {
            return null;
        }
        return data.get(field).asText();
    }

    private String textOrDefault(JsonNode data, String field, String fallback) {
        String value = text(data, field);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
