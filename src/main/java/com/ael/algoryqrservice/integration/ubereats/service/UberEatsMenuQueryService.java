package com.ael.algoryqrservice.integration.ubereats.service;

import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UberEatsMenuQueryService {

    private final UberEatsConnectionService connectionService;
    private final UberEatsClient uberEatsClient;
    private final UberEatsPayloadMapper payloadMapper;

    @Transactional(readOnly = true)
    public UberEatsDtos.ProductPageResponse listProducts(String query, int page, int size) {
        UberEatsConnection connection = connectionService.requireConnected();
        List<UberEatsDtos.ProductResponse> products = payloadMapper.toProducts(
                uberEatsClient.getMenu(connectionService.decrypt(connection))
        );
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<UberEatsDtos.ProductResponse> filtered = needle.isEmpty()
                ? products
                : products.stream()
                .filter(product -> matches(product, needle))
                .toList();
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil(filtered.size() / (double) safeSize);
        return UberEatsDtos.ProductPageResponse.builder()
                .content(filtered.subList(from, to))
                .page(safePage)
                .size(safeSize)
                .totalElements(filtered.size())
                .totalPages(totalPages)
                .build();
    }

    private boolean matches(UberEatsDtos.ProductResponse product, String needle) {
        return contains(product.getName(), needle)
                || contains(product.getDescription(), needle)
                || contains(product.getCategoryName(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
