package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.config.AiServiceProperties;
import com.ael.algoryqrservice.model.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.service.AiMenuImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/menu-import")
@RequiredArgsConstructor
public class InternalAiMenuImportController {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final AiMenuImportService aiMenuImportService;
    private final AiServiceProperties aiServiceProperties;

    @PostMapping("/publish")
    public ResponseEntity<AiMenuImportDtos.PublishResponse> publish(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody AiMenuImportDtos.PublishRequest request
    ) {
        requireServiceKey(apiKey);
        return ResponseEntity.ok(aiMenuImportService.publishProducts(request));
    }

    private void requireServiceKey(String apiKey) {
        String expected = aiServiceProperties.getApiKey();
        if (expected == null || expected.isBlank() || apiKey == null || !expected.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Geçersiz servis anahtarı");
        }
    }
}
