package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.FulfillmentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MerchantLoginPackageLookup {

    private final FulfillmentRepositoryPort fulfillmentRepository;

    @Transactional(readOnly = true)
    public Optional<ActivePackage> findActive(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return Optional.empty();
        }
        return fulfillmentRepository.findActiveByMerchantId(merchantId)
                .filter(active -> active.status() == PackageStatus.ACTIVE);
    }
}
