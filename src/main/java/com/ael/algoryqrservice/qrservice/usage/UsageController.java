package com.ael.algoryqrservice.qrservice.usage;

import com.ael.algoryqrservice.fulfillmentservice.application.metering.MeteringService;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.security.ProductUsageGateway;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

/**
 * The signed-in merchant's per-period allowances. Features charged by the backend itself (smart
 * reports, AI menu import) are only readable here; the dashboard assistant runs outside qr-service,
 * so its questions are charged and given back through this endpoint.
 */
@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    /** Features a client may spend itself. */
    private static final Set<String> CLIENT_METERED = Set.of(CatalogProducts.ASSISTANT_QUESTION);

    public record UsageResponse(String feature, String period, long used, Long limit, Long remaining,
                                boolean unlimited, Instant resetsAt) {
        static UsageResponse of(MeteringService.Usage usage) {
            return new UsageResponse(usage.feature(), usage.periodKey(), usage.used(), usage.limit(),
                    usage.remaining(), usage.unlimited(), usage.resetsAt());
        }
    }

    public record SpendRequest(@NotBlank @Size(max = 100) String idempotencyKey, @Size(max = 100) String reference) {
    }

    private final FulfillmentServiceClient fulfillmentClient;
    private final ProductUsageGateway productUsageGateway;
    private final SecurityUtils securityUtils;

    @GetMapping("/{feature}")
    public ResponseEntity<UsageResponse> usage(@PathVariable String feature) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        try {
            return ResponseEntity.ok(UsageResponse.of(fulfillmentClient.meterUsage(merchantId, feature)));
        } catch (IllegalArgumentException notMetered) {
            throw new BadRequestException("FEATURE_NOT_METERED", "Dönemlik kotası olan bir özellik değil: " + feature);
        }
    }

    /** Spends one use, or answers 403 {@code LIMIT_REACHED} with the numbers and upgrades. */
    @PostMapping("/{feature}/consume")
    @Transactional
    public ResponseEntity<UsageResponse> consume(@PathVariable String feature, @Valid @RequestBody SpendRequest request) {
        requireClientMetered(feature);
        Long merchantId = securityUtils.getCurrentMerchantId();
        MeteringService.Charge charge = productUsageGateway.meter(
                merchantId, feature, 1, scopedKey(merchantId, feature, request.idempotencyKey()), request.reference());
        return ResponseEntity.ok(UsageResponse.of(charge.usage()));
    }

    /** Gives a spend back, e.g. a question the assistant could not answer. */
    @PostMapping("/{feature}/release")
    @Transactional
    public ResponseEntity<UsageResponse> release(@PathVariable String feature, @Valid @RequestBody SpendRequest request) {
        requireClientMetered(feature);
        Long merchantId = securityUtils.getCurrentMerchantId();
        fulfillmentClient.releaseMeter(scopedKey(merchantId, feature, request.idempotencyKey()));
        return ResponseEntity.ok(UsageResponse.of(fulfillmentClient.meterUsage(merchantId, feature)));
    }

    private static void requireClientMetered(String feature) {
        if (!CLIENT_METERED.contains(feature)) {
            throw new BadRequestException("FEATURE_NOT_CLIENT_METERED", "Bu kota yalnızca sunucu tarafında harcanır");
        }
    }

    /** Keys are per merchant and feature, so one merchant can never release another's spend. */
    private static String scopedKey(Long merchantId, String feature, String key) {
        return "client:" + merchantId + ":" + feature + ":" + key.trim();
    }
}
