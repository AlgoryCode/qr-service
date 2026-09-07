package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.BranchDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/features/QR_BRANCH")
@RequiredArgsConstructor
public class FeatureBranchController {

    private final BranchService branchService;

    @PostMapping("/branches")
    @RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
    public ResponseEntity<BranchDtos.Response> create(@Valid @RequestBody BranchDtos.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(request));
    }
}
