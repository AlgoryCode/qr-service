package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.messaging.IntegrationMessagePublisher;
import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import com.ael.algoryqrservice.model.dto.IntegrationPendingProductDtos;
import com.ael.algoryqrservice.model.enums.IntegrationJobStatus;
import com.ael.algoryqrservice.model.enums.IntegrationPublishTarget;
import com.ael.algoryqrservice.repository.IntegrationPendingProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationApprovalService {

    private final IntegrationPendingProductRepository repository;
    private final MenuService menuService;
    private final IntegrationMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<IntegrationPendingProductDtos.Response> list(Long menuId, String status, Pageable pageable) {
        menuService.requireOwnedMenu(menuId);
        String approvalStatus = status == null || status.isBlank()
                ? IntegrationJobStatus.WAITING_APPROVAL
                : status.trim();
        return repository.findByMenuIdAndApprovalStatusOrderByCreatedAtAsc(menuId, approvalStatus, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public IntegrationPendingProductDtos.Response update(
            Long menuId,
            UUID id,
            IntegrationPendingProductDtos.UpdateRequest request
    ) {
        IntegrationPendingProduct product = requireWaitingProduct(menuId, id);
        ObjectNode data = product.getProductData() == null || !product.getProductData().isObject()
                ? objectMapper.createObjectNode()
                : (ObjectNode) product.getProductData().deepCopy();
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
        if (request.getModifiers() != null) {
            data.set("modifiers", request.getModifiers());
        }
        product.setProductData(data);
        product.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(product));
    }

    @Transactional
    public IntegrationPendingProductDtos.Response approve(
            Long menuId,
            UUID id,
            IntegrationPendingProductDtos.ApprovalRequest request,
            Long userId
    ) {
        IntegrationPendingProduct product = requireWaitingProduct(menuId, id);
        validateTargets(request.getPublishTargets());
        product.setApprovalStatus(IntegrationJobStatus.APPROVED);
        product.setPublishTargets(new LinkedHashSet<>(request.getPublishTargets()));
        product.setApprovedBy(userId);
        product.setApprovedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        IntegrationPendingProduct saved = repository.save(product);
        messagePublisher.publishApprovedProduct(saved);
        return toResponse(saved);
    }

    @Transactional
    public List<IntegrationPendingProductDtos.Response> bulkApprove(
            Long menuId,
            IntegrationPendingProductDtos.BulkApproveRequest request,
            Long userId
    ) {
        validateTargets(request.getPublishTargets());
        menuService.requireOwnedMenu(menuId);
        List<IntegrationPendingProduct> products = repository.findByMenuIdAndIdIn(menuId, request.getProductIds());
        if (products.size() != request.getProductIds().size()) {
            throw new BadRequestException("Bazı ürünler bulunamadı");
        }
        LocalDateTime now = LocalDateTime.now();
        List<IntegrationPendingProductDtos.Response> responses = new ArrayList<>();
        for (IntegrationPendingProduct product : products) {
            if (!IntegrationJobStatus.WAITING_APPROVAL.equals(product.getApprovalStatus())) {
                throw new BadRequestException("Yalnızca onay bekleyen ürünler onaylanabilir");
            }
            product.setApprovalStatus(IntegrationJobStatus.APPROVED);
            product.setPublishTargets(new LinkedHashSet<>(request.getPublishTargets()));
            product.setApprovedBy(userId);
            product.setApprovedAt(now);
            product.setUpdatedAt(now);
            IntegrationPendingProduct saved = repository.save(product);
            messagePublisher.publishApprovedProduct(saved);
            responses.add(toResponse(saved));
        }
        return responses;
    }

    @Transactional
    public void reject(Long menuId, UUID id, IntegrationPendingProductDtos.RejectRequest request) {
        IntegrationPendingProduct product = requireWaitingProduct(menuId, id);
        product.setApprovalStatus(IntegrationJobStatus.REJECTED);
        product.setErrorMessage(request.getReason());
        product.setUpdatedAt(LocalDateTime.now());
        repository.save(product);
    }

    private IntegrationPendingProduct requireWaitingProduct(Long menuId, UUID id) {
        menuService.requireOwnedMenu(menuId);
        IntegrationPendingProduct product = repository.findById(id)
                .orElseThrow(() -> new BadRequestException("Onay bekleyen ürün bulunamadı"));
        if (!menuId.equals(product.getMenuId())) {
            throw new BadRequestException("Ürün bu menüye ait değil");
        }
        if (!IntegrationJobStatus.WAITING_APPROVAL.equals(product.getApprovalStatus())) {
            throw new BadRequestException("Ürün onay bekleyen durumda değil");
        }
        return product;
    }

    private void validateTargets(Set<String> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new BadRequestException("Yayın hedefi zorunludur");
        }
        if (!IntegrationPublishTarget.ALL.containsAll(targets)) {
            throw new BadRequestException("Geçersiz yayın hedefi");
        }
    }

    private void putIfPresent(ObjectNode data, String field, String value) {
        if (value != null) {
            data.put(field, value);
        }
    }

    private IntegrationPendingProductDtos.Response toResponse(IntegrationPendingProduct product) {
        List<String> warnings = new ArrayList<>();
        JsonNode warningsNode = product.getProductData() == null ? null : product.getProductData().get("warnings");
        if (warningsNode != null && warningsNode.isArray()) {
            for (JsonNode warning : warningsNode) {
                if (warning != null && !warning.isNull()) {
                    warnings.add(warning.asText());
                }
            }
        }
        return IntegrationPendingProductDtos.Response.builder()
                .id(product.getId())
                .jobId(product.getJobId())
                .menuId(product.getMenuId())
                .source(product.getSource())
                .sourceProductId(product.getSourceProductId())
                .productData(product.getProductData())
                .confidence(product.getConfidence())
                .approvalStatus(product.getApprovalStatus())
                .publishTargets(product.getPublishTargets())
                .warnings(warnings)
                .errorMessage(product.getErrorMessage())
                .build();
    }
}
