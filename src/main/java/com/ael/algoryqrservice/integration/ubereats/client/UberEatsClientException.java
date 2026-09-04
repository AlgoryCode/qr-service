package com.ael.algoryqrservice.integration.ubereats.client;

public class UberEatsClientException extends RuntimeException {

    public UberEatsClientException(String message) {
        super(message);
    }

    public UberEatsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
