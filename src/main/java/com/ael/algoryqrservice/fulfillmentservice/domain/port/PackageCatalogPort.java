package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PackageCatalogPort {

    Optional<PlanPackage> findByCode(String code);

    Optional<PlanPackage> findById(Long id);

    Page<PlanPackage> findActive(Pageable pageable);

    Page<PlanPackage> findPurchasable(Pageable pageable);
}
