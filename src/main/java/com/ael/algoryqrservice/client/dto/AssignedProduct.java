package com.ael.algoryqrservice.client.dto;

public record AssignedProduct(
        String productCode,
        String productName,
        int quantity,
        boolean unlimited
) {
}
