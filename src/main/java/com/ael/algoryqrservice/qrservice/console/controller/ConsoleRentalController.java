package com.ael.algoryqrservice.qrservice.console.controller;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleCatalogService;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/**
 * Konsol Müşteri Takip: hourly game console rentals. Owner only; staff tokens are refused by
 * {@link com.ael.algoryqrservice.qrservice.security.StaffPathGatewayFilter} since this is not a staff path.
 */
@RestController
@RequestMapping("/api/v1/console")
@RequiredArgsConstructor
public class ConsoleRentalController {

    private final ConsoleCatalogService catalogService;
    private final ConsoleSessionService sessionService;

    @GetMapping("/branches/{branchId}/consoles")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<List<ConsoleDtos.ConsoleResponse>> listConsoles(@PathVariable Long branchId) {
        return ResponseEntity.ok(catalogService.list(branchId));
    }

    @PostMapping("/branches/{branchId}/consoles")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> createConsole(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.ConsoleRequest request
    ) {
        return ResponseEntity.status(201).body(catalogService.create(branchId, request));
    }

    @PutMapping("/consoles/{consoleId}")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> updateConsole(
            @PathVariable Long consoleId,
            @Valid @RequestBody ConsoleDtos.ConsoleRequest request
    ) {
        return ResponseEntity.ok(catalogService.update(consoleId, request));
    }

    @DeleteMapping("/consoles/{consoleId}")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<Void> deleteConsole(@PathVariable Long consoleId) {
        catalogService.delete(consoleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branches/{branchId}/rate")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<ConsoleDtos.RateSettingResponse> getRate(@PathVariable Long branchId) {
        return ResponseEntity.ok(catalogService.getRate(branchId));
    }

    @PutMapping("/branches/{branchId}/rate")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<ConsoleDtos.RateSettingResponse> updateRate(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.RateSettingRequest request
    ) {
        return ResponseEntity.ok(catalogService.updateRate(branchId, request));
    }

    @PostMapping("/consoles/{consoleId}/sessions")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<ConsoleDtos.SessionResponse> startSession(
            @PathVariable Long consoleId,
            @Valid @RequestBody(required = false) ConsoleDtos.StartSessionRequest request
    ) {
        return ResponseEntity.status(201).body(sessionService.start(consoleId, request));
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<ConsoleDtos.SessionResponse> finishSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.finish(sessionId));
    }

    @GetMapping("/branches/{branchId}/sessions/active")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<List<ConsoleDtos.SessionResponse>> listActive(@PathVariable Long branchId) {
        return ResponseEntity.ok(sessionService.listActive(branchId));
    }

    @GetMapping("/branches/{branchId}/sessions")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<ConsoleDtos.SessionPageResponse> listHistory(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(sessionService.listHistory(branchId, page, size));
    }
}
