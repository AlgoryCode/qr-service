package com.ael.algoryqrservice.fulfillmentservice.domain.exception;

public class InvalidUsageRequestException extends DomainException {

    public InvalidUsageRequestException(String message) {
        super("INVALID_USAGE", message);
    }
}
