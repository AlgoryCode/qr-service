package com.ael.algoryqrservice.qrservice.billing.lifecycle;

import com.ael.algoryqrservice.fulfillmentservice.application.service.SubscriptionLifecycleService;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.qrservice.billing.dto.PlanPackageResponse;
import com.ael.algoryqrservice.qrservice.billing.service.PublicPackageCatalogService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Timeline for the merchant's "Planım" page. */
@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionHistoryController {

    private static final int MAX_LIMIT = 100;

    private final SubscriptionLifecycleService lifecycle;
    private final PublicPackageCatalogService catalog;
    private final SecurityUtils securityUtils;

    @GetMapping("/history")
    public List<SubscriptionHistoryItem> history(@RequestParam(defaultValue = "30") int limit) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        List<SubscriptionEvent> events = lifecycle.history(merchantId, Math.max(1, Math.min(limit, MAX_LIMIT)));
        Map<String, String> names = new HashMap<>();
        return events.stream()
                .map(event -> new SubscriptionHistoryItem(
                        event.type().name(),
                        event.packageCode(),
                        name(names, event.packageCode()),
                        event.previousPackageCode(),
                        name(names, event.previousPackageCode()),
                        event.endsAt(),
                        event.occurredAt()))
                .toList();
    }

    private String name(Map<String, String> cache, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(code, c -> catalog.findByCode(c).map(PlanPackageResponse::getName).orElse(c));
    }

    public record SubscriptionHistoryItem(
            String type,
            String packageCode,
            String packageName,
            String previousPackageCode,
            String previousPackageName,
            Instant endsAt,
            Instant occurredAt
    ) {
    }
}
