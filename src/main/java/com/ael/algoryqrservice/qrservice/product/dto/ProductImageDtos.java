package com.ael.algoryqrservice.qrservice.product.dto;

public final class ProductImageDtos {

    private ProductImageDtos() {
    }

    public record UploadResponse(String imageUrl, String objectKey) {
    }
}
