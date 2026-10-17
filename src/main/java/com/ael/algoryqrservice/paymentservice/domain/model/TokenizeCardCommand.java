package com.ael.algoryqrservice.paymentservice.domain.model;

public record TokenizeCardCommand(
        String accountId,
        String email,
        String alias,
        String cardHolderName,
        String cardNumber,
        String expireMonth,
        String expireYear,
        String existingCardUserKey
) {

    @Override
    public String toString() {
        return "TokenizeCardCommand[accountId=" + accountId + ", alias=" + alias + ", card=REDACTED]";
    }
}
