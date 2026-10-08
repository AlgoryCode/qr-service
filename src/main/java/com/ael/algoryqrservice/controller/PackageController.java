package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.service.PublicPackageCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
public class PackageController {

    private final PublicPackageCatalogService publicPackageCatalogService;

    @GetMapping
    public ResponseEntity<List<PlanPackageResponse>> list(@RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(publicPackageCatalogService.list(active));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanPackageResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(publicPackageCatalogService.getById(id));
    }
}
