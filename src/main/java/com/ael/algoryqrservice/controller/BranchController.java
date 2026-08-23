package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.BranchDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.ListResponse> list() {
        return ResponseEntity.ok(branchService.listMine());
    }

    @PostMapping
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.Response> create(@Valid @RequestBody BranchDtos.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(request));
    }

    @GetMapping("/{branchId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.Response> get(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchService.getMine(branchId));
    }

    @PutMapping("/{branchId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.Response> update(
            @PathVariable Long branchId,
            @Valid @RequestBody BranchDtos.UpdateRequest request
    ) {
        return ResponseEntity.ok(branchService.update(branchId, request));
    }

    @PostMapping("/{branchId}/photo")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.Response> uploadPhoto(
            @PathVariable Long branchId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.uploadPhoto(branchId, file));
    }

    @DeleteMapping("/{branchId}/photo")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.Response> clearPhoto(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchService.clearPhoto(branchId));
    }

    @PostMapping("/{branchId}/photo/apply-all-branches")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.ListResponse> applyPhotoToAllBranches(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchService.applyPhotoToAllBranches(branchId));
    }

    @PostMapping("/{branchId}/photo/apply-all-menus")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.ListResponse> applyPhotoToAllMenus(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchService.applyPhotoToAllMenus(branchId));
    }

    @DeleteMapping("/{branchId}")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<Void> delete(@PathVariable Long branchId) {
        branchService.delete(branchId);
        return ResponseEntity.noContent().build();
    }
}
