package com.ael.algoryqrservice.store.controller;

import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.security.RequiresProductScope;
import com.ael.algoryqrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.store.service.StoreCourierService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/store/couriers")
@RequiredArgsConstructor
@RequiresProductScope(CatalogScopes.ONLINE_ORDER_OWNER)
public class StoreCourierController {

    private final StoreCourierService storeCourierService;

    @GetMapping
    public ResponseEntity<List<StoreDtos.CourierResponse>> list() {
        return ResponseEntity.ok(storeCourierService.list());
    }

    @PostMapping
    public ResponseEntity<StoreDtos.CourierResponse> create(@Valid @RequestBody StoreDtos.CourierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeCourierService.create(request));
    }

    @PutMapping("/{courierId}")
    public ResponseEntity<StoreDtos.CourierResponse> update(
            @PathVariable Long courierId,
            @Valid @RequestBody StoreDtos.CourierRequest request
    ) {
        return ResponseEntity.ok(storeCourierService.update(courierId, request));
    }

    @DeleteMapping("/{courierId}")
    public ResponseEntity<Void> delete(@PathVariable Long courierId) {
        storeCourierService.delete(courierId);
        return ResponseEntity.noContent().build();
    }
}
