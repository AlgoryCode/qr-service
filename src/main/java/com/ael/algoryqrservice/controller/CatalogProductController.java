package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.ProductResponse;
import com.ael.algoryqrservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class CatalogProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getActiveProducts() {
        return ResponseEntity.ok(productService.getActive());
    }

    @GetMapping("/{code}")
    public ResponseEntity<ProductResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(productService.getByCode(code));
    }
}
