package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackageItem;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PlanVersionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlanVersionAdapter implements PlanVersionPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<List<PlanPackageItem>> linesOfPurchase(Long purchaseId) {
        if (purchaseId == null || purchaseId <= 0) {
            return Optional.empty();
        }
        List<Long> version = jdbcTemplate.queryForList(
                "select plan_version_id from purchases where id = ? and plan_version_id is not null",
                Long.class, purchaseId);
        if (version.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(jdbcTemplate.query("""
                select vi.id, p.id as product_id, p.code, p.feature_code, p.scope_code, p.type_id,
                       vi.quantity, vi.unlimited
                from plan_version_items vi
                join catalog_products p on p.id = vi.product_id
                where vi.plan_version_id = ?
                order by vi.id
                """,
                (rs, row) -> new PlanPackageItem(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("code"),
                        rs.getString("feature_code"),
                        rs.getString("scope_code"),
                        productType(rs.getString("type_id")),
                        rs.getInt("quantity"),
                        rs.getBoolean("unlimited")),
                version.get(0)));
    }

    private static ProductType productType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return ProductType.PACKAGE_PRODUCT;
        }
        try {
            return ProductType.valueOf(typeId);
        } catch (IllegalArgumentException exception) {
            return ProductType.PACKAGE_PRODUCT;
        }
    }
}
