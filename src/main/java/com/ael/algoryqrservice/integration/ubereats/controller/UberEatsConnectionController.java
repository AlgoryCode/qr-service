package com.ael.algoryqrservice.integration.ubereats.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsConnectionService;
import com.ael.algoryqrservice.security.RequiresProductScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/integrations/ubereats-menu")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.QR_MENU_OWNER)
public class UberEatsConnectionController {

    private final UberEatsConnectionService connectionService;

    @GetMapping("/connections")
    public ResponseEntity<List<UberEatsDtos.ConnectionResponse>> listConnections() {
        return ResponseEntity.ok(connectionService.listMine());
    }

    @GetMapping("/connections/{menuId}")
    public ResponseEntity<UberEatsDtos.ConnectionResponse> getConnection(@PathVariable Long menuId) {
        return ResponseEntity.ok(connectionService.getMine(menuId));
    }

    @PutMapping("/connections")
    public ResponseEntity<UberEatsDtos.ConnectionResponse> upsert(
            @Valid @RequestBody UberEatsDtos.UpsertConnectionRequest request
    ) {
        return ResponseEntity.ok(connectionService.upsert(request));
    }

    @DeleteMapping("/connections/{menuId}")
    public ResponseEntity<UberEatsDtos.ConnectionResponse> disconnect(@PathVariable Long menuId) {
        return ResponseEntity.ok(connectionService.disconnect(menuId));
    }
}
