package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface FulfillmentRepositoryPort {

    Optional<PackageFulfillment> findByEventId(String eventId);

    Optional<PackageFulfillment> findById(Long id);

    PackageFulfillment saveFulfillment(PackageFulfillment fulfillment);

    void supersedeOtherFulfillments(Long merchantId, Long keepFulfillmentId);

    Optional<ActivePackage> findActiveByUserId(Long merchantId);

    ActivePackage upsertActivePackage(ActivePackage activePackage);

    Page<PackageFulfillment> findByMerchantId(Long merchantId, Pageable pageable);
}
