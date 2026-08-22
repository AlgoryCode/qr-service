package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.service.PackageCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PackageCatalogInitializer implements ApplicationRunner {

    private final PackageCatalogService packageCatalogService;
    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!appProperties.getSeed().isPackageCatalog()) {
            return;
        }
        packageCatalogService.ensureCatalogProducts();
    }
}
