package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.PlanPackageRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.service.PlanPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/packages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlanPackageController {

    private final PlanPackageService planPackageService;

    @PostMapping
    public ResponseEntity<PlanPackageResponse> create(@Valid @RequestBody PlanPackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planPackageService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<PlanPackageResponse>> getAll() {
        return ResponseEntity.ok(planPackageService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanPackageResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(planPackageService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanPackageResponse> update(@PathVariable Long id, @Valid @RequestBody PlanPackageRequest request) {
        return ResponseEntity.ok(planPackageService.update(id, request));
    }
}
