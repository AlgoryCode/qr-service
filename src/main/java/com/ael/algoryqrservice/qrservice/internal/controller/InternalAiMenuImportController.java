package com.ael.algoryqrservice.qrservice.internal.controller;

import com.ael.algoryqrservice.qrservice.menu.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.qrservice.menu.service.AiMenuImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/menu-import")
@RequiredArgsConstructor
public class InternalAiMenuImportController {

    private final AiMenuImportService aiMenuImportService;

    @PostMapping("/publish")
    public ResponseEntity<AiMenuImportDtos.PublishResponse> publish(
            @RequestBody AiMenuImportDtos.PublishRequest request
    ) {
        return ResponseEntity.ok(aiMenuImportService.publishProducts(request));
    }
}
