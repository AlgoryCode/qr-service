package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.fulfillmentservice.application.snapshot.EntitlementSnapshotService;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.CountedResourcePort;
import com.ael.algoryqrservice.qrservice.model.Feature;
import com.ael.algoryqrservice.qrservice.repository.FeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** What a member may use right now, with live usage for capacity features, for the admin's member view. */
@Service
@RequiredArgsConstructor
public class AdminEntitlementService {

    public record Line(String feature, String name, Feature.Kind kind, String unit, long limit, boolean unlimited,
                       Long used, int grants, Instant validUntil) {
    }

    public record Subscription(String packageCode, String status, String billingStatus, Integer planVersion,
                               LocalDate periodEnd, boolean cancelAtPeriodEnd) {
    }

    public record View(long version, Subscription subscription, List<Line> lines) {
    }

    private final EntitlementSnapshotService snapshots;
    private final CountedResourcePort countedResources;
    private final FeatureRepository features;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public View forMerchant(Long merchantId) {
        EntitlementSnapshotService.Snapshot snapshot = snapshots.read(merchantId);
        Map<String, Feature> catalog = features.findAll().stream()
                .collect(Collectors.toMap(Feature::getKey, Function.identity()));
        List<Line> lines = snapshot.features().values().stream()
                .map(effective -> {
                    Feature feature = catalog.get(effective.feature());
                    Long used = countedResources.count(merchantId, effective.feature()).isPresent()
                            ? countedResources.count(merchantId, effective.feature()).getAsLong()
                            : null;
                    return new Line(effective.feature(),
                            feature == null ? effective.feature() : feature.getName(),
                            feature == null ? Feature.Kind.BOOLEAN : feature.getKind(),
                            feature == null ? null : feature.getUnit(),
                            effective.limit(), effective.unlimited(), used, effective.grants(), effective.validUntil());
                })
                .sorted(Comparator.comparing((Line line) -> line.kind().ordinal()).thenComparing(Line::name))
                .toList();
        List<Subscription> subscription = jdbc.query("""
                select ap.package_code, ap.status, ap.billing_status, v.version, ap.period_end, ap.cancel_at_period_end
                from merchant_subscriptions ap left join plan_versions v on v.id = ap.plan_version_id
                where ap.merchant_id = ?
                """,
                (rs, row) -> new Subscription(rs.getString(1), rs.getString(2), rs.getString(3),
                        (Integer) rs.getObject(4), rs.getDate(5) == null ? null : rs.getDate(5).toLocalDate(),
                        rs.getBoolean(6)),
                merchantId);
        return new View(snapshot.version(), subscription.isEmpty() ? null : subscription.get(0), lines);
    }
}
