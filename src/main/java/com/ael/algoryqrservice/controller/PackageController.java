package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.service.PlanPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
public class PackageController {

    private final PlanPackageService planPackageService;

    @GetMapping
    public ResponseEntity<List<PlanPackageResponse>> getActivePackages() {
        return ResponseEntity.ok(planPackageService.getActivePackages());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanPackageResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(planPackageService.getById(id));
    }
}
