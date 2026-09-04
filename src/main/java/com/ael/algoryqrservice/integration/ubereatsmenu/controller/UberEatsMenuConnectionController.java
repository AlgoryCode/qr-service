package com.ael.algoryqrservice.integration.ubereatsmenu.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.integration.ubereatsmenu.model.dto.UberEatsMenuDtos;
import com.ael.algoryqrservice.integration.ubereatsmenu.service.UberEatsMenuConnectionService;
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
public class UberEatsMenuConnectionController {

    private final UberEatsMenuConnectionService connectionService;

    @GetMapping("/connections")
    public ResponseEntity<List<UberEatsMenuDtos.ConnectionResponse>> listConnections() {
        return ResponseEntity.ok(connectionService.listMine());
    }

    @GetMapping("/connections/{menuId}")
    public ResponseEntity<UberEatsMenuDtos.ConnectionResponse> getConnection(@PathVariable Long menuId) {
        return ResponseEntity.ok(connectionService.getMine(menuId));
    }

    @PutMapping("/connections")
    public ResponseEntity<UberEatsMenuDtos.ConnectionResponse> upsert(
            @Valid @RequestBody UberEatsMenuDtos.UpsertConnectionRequest request
    ) {
        return ResponseEntity.ok(connectionService.upsert(request));
    }

    @DeleteMapping("/connections/{menuId}")
    public ResponseEntity<UberEatsMenuDtos.ConnectionResponse> disconnect(@PathVariable Long menuId) {
        return ResponseEntity.ok(connectionService.disconnect(menuId));
    }
}
