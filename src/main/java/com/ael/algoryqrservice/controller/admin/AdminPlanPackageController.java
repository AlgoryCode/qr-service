package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.model.dto.PlanPackageItemRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.dto.PublishPackageRequest;
import com.ael.algoryqrservice.service.PlanPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<PlanPackageResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PlanPackageRequest request
    ) {
        return ResponseEntity.ok(planPackageService.update(id, request));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<PlanPackageResponse> addItem(
            @PathVariable Long id,
            @Valid @RequestBody PlanPackageItemRequest request
    ) {
        return ResponseEntity.ok(planPackageService.addItem(id, request));
    }

    @DeleteMapping("/{id}/items/{productId}")
    public ResponseEntity<PlanPackageResponse> removeItem(
            @PathVariable Long id,
            @PathVariable Long productId
    ) {
        return ResponseEntity.ok(planPackageService.removeItem(id, productId));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<PlanPackageResponse> publish(
            @PathVariable Long id,
            @RequestBody(required = false) PublishPackageRequest request
    ) {
        PublishPackageRequest body = request == null ? new PublishPackageRequest() : request;
        return ResponseEntity.ok(planPackageService.publish(id, body));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PlanPackageResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body
    ) {
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(planPackageService.updateActiveStatus(id, active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        planPackageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
