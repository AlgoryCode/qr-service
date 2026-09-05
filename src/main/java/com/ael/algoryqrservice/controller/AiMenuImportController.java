package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.AiMenuImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/menus/{menuId}/ai-import")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.AI_MENU_IMPORT_OWNER)
public class AiMenuImportController {

    private final AiMenuImportService aiMenuImportService;

    @PostMapping("/jobs")
    public ResponseEntity<AiMenuImportDtos.JobAccepted> createJob(
            @PathVariable Long menuId,
            @Valid @RequestBody AiMenuImportDtos.CreateJobRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(aiMenuImportService.createJob(menuId, request));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AiMenuImportDtos.JobResponse> getJob(
            @PathVariable Long menuId,
            @PathVariable UUID jobId
    ) {
        return ResponseEntity.ok(aiMenuImportService.getJob(menuId, jobId));
    }
}
