package com.ael.algoryqrservice.qrservice.billing.dto;

public record ConsumedEntitlement(Long purchaseId, Long entitlementId, int amount) {
}
