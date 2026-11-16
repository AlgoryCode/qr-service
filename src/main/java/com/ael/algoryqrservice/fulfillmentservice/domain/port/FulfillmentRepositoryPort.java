package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.time.Instant;

public interface FulfillmentRepositoryPort {

    Optional<PackageFulfillment> findByEventId(String eventId);

    Optional<PackageFulfillment> findById(Long id);

    PackageFulfillment saveFulfillment(PackageFulfillment fulfillment);

    void supersedeOtherFulfillments(Long merchantId, Long keepFulfillmentId);

    Optional<ActivePackage> findActiveByMerchantId(Long merchantId);

    ActivePackage upsertActivePackage(ActivePackage activePackage);

    Page<PackageFulfillment> findByMerchantId(Long merchantId, Pageable pageable);

    /** Writes status and grace fields of an existing row without touching its activation time. */
    ActivePackage saveActivePackage(ActivePackage activePackage);

    /**
     * Packages whose lifecycle must move on: a non-free package past its period end, or a grace
     * period that has run out. Oldest first, at most {@code limit}.
     */
    List<ActivePackage> findLifecycleDue(LocalDate today, Instant now, int limit);

    /** Trials whose last day falls in {@code [from, to]}, for reminder mails. */
    List<ActivePackage> findTrialsEndingBetween(LocalDate from, LocalDate to);

    /** Whether the merchant ever had any package (trial, paid, grant or free). */
    boolean hasAnyFulfillment(Long merchantId);
}
