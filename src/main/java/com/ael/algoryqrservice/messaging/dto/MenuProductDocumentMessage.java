package com.ael.algoryqrservice.messaging.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Denormalized product snapshot carried inside {@link MenuProductIndexMessage}.
 * Everything the vector indexer needs travels in the message so ai-service never
 * reads the menu database.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MenuProductDocumentMessage(
        Long productId,
        Long menuId,
        String name,
        String description,
        String mainCategorySlug,
        String mainCategoryName,
        String subCategorySlug,
        String subCategoryName,
        List<String> tagSlugs,
        List<String> tagNames,
        List<String> allergenSlugs,
        List<String> allergenNames,
        BigDecimal price,
        String currency,
        Boolean available,
        Boolean chefRecommended,
        Integer servesPeopleMin,
        Integer servesPeopleMax,
        BigDecimal energyKcal,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal carbohydrate,
        BigDecimal salt,
        BigDecimal ratingAvg,
        Long ratingCount,
        Instant updatedAt
) {
}
