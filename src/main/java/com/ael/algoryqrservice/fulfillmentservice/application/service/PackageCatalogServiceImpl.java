package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.exception.ResourceNotFoundException;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PackageCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PackageCatalogServiceImpl implements PackageCatalogService {

    private final PackageCatalogPort packageCatalogPort;

    @Override
    @Transactional(readOnly = true)
    public PlanPackage requireByCode(String code) {
        return packageCatalogPort.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + code));
    }

    @Override
    @Transactional(readOnly = true)
    public PlanPackage requireById(Long id) {
        return packageCatalogPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlanPackage> listActive(Pageable pageable) {
        return packageCatalogPort.findActive(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlanPackage> listPurchasable(Pageable pageable) {
        return packageCatalogPort.findPurchasable(pageable);
    }
}
