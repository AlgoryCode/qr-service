package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.CatalogSeedDtos;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.dto.SellablePackageComposeRequest;
import com.ael.algoryqrservice.service.CatalogSeedService;
import com.ael.algoryqrservice.service.CatalogWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogWorkflowController {

    private final CatalogWorkflowService catalogWorkflowService;
    private final CatalogSeedService catalogSeedService;

    @PostMapping("/sellable-packages")
    public ResponseEntity<PlanPackageResponse> createSellablePackage(
            @Valid @RequestBody SellablePackageComposeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogWorkflowService.createSellablePackage(request));
    }

    @PostMapping("/import")
    public ResponseEntity<CatalogSeedDtos.ImportResult> importCatalog(
            @RequestParam(defaultValue = "false") boolean useClasspathSeed,
            @RequestBody(required = false) CatalogSeedDtos.Document document
    ) {
        CatalogSeedDtos.ImportResult result = useClasspathSeed
                ? catalogSeedService.importClasspathSeed()
                : catalogSeedService.importDocument(document);
        return ResponseEntity.ok(result);
    }
}
