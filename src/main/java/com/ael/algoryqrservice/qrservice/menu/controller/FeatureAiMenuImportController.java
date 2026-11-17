package com.ael.algoryqrservice.qrservice.menu.controller;

import com.ael.algoryqrservice.qrservice.menu.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.RequiresFeature;
import com.ael.algoryqrservice.qrservice.menu.service.AiMenuImportService;
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
@RequestMapping("/api/v1/features/AI_MENU_IMPORT")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.AI_MENU_IMPORT)
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
