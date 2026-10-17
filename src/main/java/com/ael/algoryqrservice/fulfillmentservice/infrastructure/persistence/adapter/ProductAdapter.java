package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.CatalogProduct;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.ProductPort;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.ProductEntity;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductAdapter implements ProductPort {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Optional<CatalogProduct> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return productJpaRepository.findByCode(code).map(this::toDomain);
    }

    @Override
    public Map<Long, CatalogProduct> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return productJpaRepository.findByIdIn(ids).stream()
                .map(this::toDomain)
                .collect(Collectors.toMap(CatalogProduct::id, Function.identity()));
    }

    private CatalogProduct toDomain(ProductEntity entity) {
        return new CatalogProduct(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getFeatureCode(),
                entity.getScopeCode()
        );
    }
}
