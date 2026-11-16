package com.ael.algoryqrservice.fulfillmentservice.domain.model;

import java.time.Instant;

/**
 * One recorded change of a merchant's subscription. Also published as a Spring application event
 * after the surrounding transaction commits, so mails and the public menu follow the change.
 *
 * @param dedupeKey set for events that must happen once (e.g. a trial reminder for a given day)
 * @param endsAt    the date the user should know about: trial end, grace end, new period end
 */
public record SubscriptionEvent(
        Long merchantId,
        SubscriptionEventType type,
        String packageCode,
        String previousPackageCode,
        PackageStatus status,
        Instant endsAt,
        String detail,
        String actor,
        String dedupeKey,
        Instant occurredAt
) {

    public static SubscriptionEvent of(
            Long merchantId,
            SubscriptionEventType type,
            String packageCode,
            String previousPackageCode,
            PackageStatus status,
            Instant endsAt,
            String detail
    ) {
        return new SubscriptionEvent(merchantId, type, packageCode, previousPackageCode, status, endsAt,
                detail, "system", null, Instant.now());
    }

    public SubscriptionEvent withDedupeKey(String key) {
        return new SubscriptionEvent(merchantId, type, packageCode, previousPackageCode, status, endsAt,
                detail, actor, key, occurredAt);
    }

    public SubscriptionEvent withActor(String newActor) {
        return new SubscriptionEvent(merchantId, type, packageCode, previousPackageCode, status, endsAt,
                detail, newActor == null || newActor.isBlank() ? "system" : newActor, dedupeKey, occurredAt);
    }
}
