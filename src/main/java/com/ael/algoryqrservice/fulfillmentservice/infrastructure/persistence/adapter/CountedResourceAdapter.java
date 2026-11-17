package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.port.CountedResourcePort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.OptionalLong;

@Component
@RequiredArgsConstructor
public class CountedResourceAdapter implements CountedResourcePort {

    /**
     * What counts against each capacity feature. A menu QR is a QR that a live menu points at; every
     * other live QR is a plain QR code. Products of a deleted menu no longer count, nor do branches
     * locked for being over the limit.
     */
    private static final Map<String, String> COUNT_SQL = Map.of(
            "QR_BRANCH",
            "select count(*) from tbl_branch where merchant_id = ? and is_deleted = false and capacity_locked_at is null",
            "QR_MENU",
            "select count(*) from tbl_menu where merchant_id = ? and is_deleted = false and qr_id is not null",
            "QR_CREATE",
            """
            select count(*) from tbl_qr q
            where q.merchant_id = ? and q.is_deleted = false
              and not exists (select 1 from tbl_menu m where m.qr_id = q.qr_id and m.is_deleted = false)
            """,
            "MENU_PRODUCT",
            """
            select count(*) from tbl_menu_products p
            where p.merchant_id = ? and p.is_deleted = false
              and not exists (select 1 from tbl_menu m where m.menu_id = p.menu_id and m.is_deleted = true)
            """
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public OptionalLong count(Long merchantId, String featureCode) {
        String sql = featureCode == null ? null : COUNT_SQL.get(featureCode);
        if (sql == null || merchantId == null) {
            return OptionalLong.empty();
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class, merchantId);
        return OptionalLong.of(count == null ? 0 : count);
    }

    @Override
    public boolean isCounted(String featureCode) {
        return featureCode != null && COUNT_SQL.containsKey(featureCode);
    }
}
