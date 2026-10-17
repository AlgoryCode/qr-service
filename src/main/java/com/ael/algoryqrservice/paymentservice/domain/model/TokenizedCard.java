package com.ael.algoryqrservice.paymentservice.domain.model;

public record TokenizedCard(
        String status,
        String errorCode,
        String errorMessage,
        String cardUserKey,
        String cardToken,
        String alias,
        String association,
        String family,
        String bankName,
        String lastFour
) {

    @Override
    public String toString() {
        return "TokenizedCard[status=" + status + ", alias=" + alias + ", lastFour=" + lastFour + "]";
    }
}
