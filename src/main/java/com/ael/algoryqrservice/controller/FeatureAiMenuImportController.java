package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.model.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.service.AiMenuImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/features/AI_MENU_IMPORT")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.AI_MENU_IMPORT_OWNER)
public class FeatureAiMenuImportController {

    private final AiMenuImportService aiMenuImportService;

    @PostMapping("/menus/{menuId}/ai-import/jobs")
    public ResponseEntity<AiMenuImportDtos.JobAccepted> createJob(
            @PathVariable Long menuId,
            @Valid @RequestBody AiMenuImportDtos.CreateJobRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(aiMenuImportService.createJob(menuId, request));
    }
}
