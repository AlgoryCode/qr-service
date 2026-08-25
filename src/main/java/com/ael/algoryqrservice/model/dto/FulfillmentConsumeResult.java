package com.ael.algoryqrservice.model.dto;

public record FulfillmentConsumeResult(int consumed, Long purchaseId, Long detailId) {

    public boolean fullyConsumed(int requested) {
        return consumed >= requested;
    }
}
