package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.ProductImageDtos;
import com.ael.algoryqrservice.service.MenuService;
import com.ael.algoryqrservice.service.ProductImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class ProductImageController {

    private final MenuService menuService;
    private final ProductImageStorageService productImageStorageService;

    @PostMapping("/{menuId}/products/images")
    public ResponseEntity<ProductImageDtos.UploadResponse> uploadProductImage(
            @PathVariable Long menuId,
            @RequestParam("file") MultipartFile file
    ) {
        menuService.requireOwnedMenu(menuId);
        return ResponseEntity.status(201).body(productImageStorageService.upload(menuId, file));
    }

    @DeleteMapping("/{menuId}/products/images")
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable Long menuId,
            @RequestParam(required = false) String objectKey,
            @RequestParam(required = false) String imageUrl
    ) {
        menuService.requireOwnedMenu(menuId);
        productImageStorageService.deleteForMenu(menuId, objectKey, imageUrl);
        return ResponseEntity.noContent().build();
    }
}
