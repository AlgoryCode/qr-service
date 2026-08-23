package com.ael.algoryqrservice.integration.trendyolgo.service;

import com.ael.algoryqrservice.integration.trendyolgo.client.TrendyolGoClient;
import com.ael.algoryqrservice.integration.trendyolgo.mapper.TrendyolGoPayloadMapper;
import com.ael.algoryqrservice.integration.trendyolgo.model.TrendyolGoConnection;
import com.ael.algoryqrservice.integration.trendyolgo.model.dto.TrendyolGoDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TrendyolGoMenuQueryService {

    private final TrendyolGoConnectionService connectionService;
    private final TrendyolGoClient trendyolGoClient;
    private final TrendyolGoPayloadMapper payloadMapper;

    @Transactional(readOnly = true)
    public TrendyolGoDtos.ProductPageResponse listProducts(Long branchId, String query, int page, int size) {
        TrendyolGoConnection connection = connectionService.requireConnected(branchId);
        List<TrendyolGoDtos.ProductResponse> products = payloadMapper.toProducts(
                trendyolGoClient.getMenu(connectionService.decrypt(connection))
        );
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<TrendyolGoDtos.ProductResponse> filtered = needle.isEmpty()
                ? products
                : products.stream()
                .filter(product -> matches(product, needle))
                .toList();
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil(filtered.size() / (double) safeSize);
        return TrendyolGoDtos.ProductPageResponse.builder()
                .content(filtered.subList(from, to))
                .page(safePage)
                .size(safeSize)
                .totalElements(filtered.size())
                .totalPages(totalPages)
                .build();
    }

    private boolean matches(TrendyolGoDtos.ProductResponse product, String needle) {
        return contains(product.getName(), needle)
                || contains(product.getDescription(), needle)
                || contains(product.getCategoryName(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
