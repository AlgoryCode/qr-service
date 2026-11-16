package com.ael.algoryqrservice.qrservice.billing.admin;

import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageRequest;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.PackageResponse;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.ProductRequest;
import com.ael.algoryqrservice.qrservice.billing.admin.AdminCatalogDtos.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final AdminCatalogService catalog;

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return catalog.listProducts();
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable Long id) {
        return catalog.getProduct(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return catalog.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return catalog.updateProduct(id, request);
    }

    @GetMapping("/packages")
    public List<PackageResponse> packages() {
        return catalog.listPackages();
    }

    @GetMapping("/packages/{id}")
    public PackageResponse planPackage(@PathVariable Long id) {
        return catalog.getPackage(id);
    }

    @PostMapping("/packages")
    @ResponseStatus(HttpStatus.CREATED)
    public PackageResponse createPackage(@Valid @RequestBody PackageRequest request) {
        return catalog.createPackage(request);
    }

    @PutMapping("/packages/{id}")
    public PackageResponse updatePackage(@PathVariable Long id, @Valid @RequestBody PackageRequest request) {
        return catalog.updatePackage(id, request);
    }
}
