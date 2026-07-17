package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.ProductRequest;
import com.ael.algoryqrservice.model.dto.ProductResponse;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String code = normalizeCode(request.getCode());
        if (productRepository.existsByCode(code)) {
            throw new BadRequestException("Bu ürün kodu zaten mevcut: " + code);
        }

        Product product = Product.builder()
                .code(code)
                .name(request.getName().trim())
                .description(request.getDescription())
                .scopeCode(normalizeCode(request.getScopeCode()))
                .consumable(Boolean.TRUE.equals(request.getConsumable()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return productRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return toResponse(findProduct(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        String code = normalizeCode(request.getCode());

        if (!product.getCode().equals(code) && productRepository.existsByCode(code)) {
            throw new BadRequestException("Bu ürün kodu zaten mevcut: " + code);
        }

        product.setCode(code);
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setScopeCode(normalizeCode(request.getScopeCode()));
        product.setConsumable(Boolean.TRUE.equals(request.getConsumable()));
        product.setActive(Boolean.TRUE.equals(request.getActive()));

        return toResponse(productRepository.save(product));
    }

    Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Ürün bulunamadı: " + id));
    }

    Product findActiveProduct(Long id) {
        Product product = findProduct(id);
        if (!product.isActive()) {
            throw new BadRequestException("Ürün aktif değil: " + id);
        }
        return product;
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .description(product.getDescription())
                .scopeCode(product.getScopeCode())
                .consumable(product.isConsumable())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
