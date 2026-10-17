package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PackageCatalogService {

    PlanPackage requireByCode(String code);

    PlanPackage requireById(Long id);

    Page<PlanPackage> listActive(Pageable pageable);

    Page<PlanPackage> listPurchasable(Pageable pageable);
}
