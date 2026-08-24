package com.ael.algoryqrservice.integration.odeal.client;

public class OdealClientException extends RuntimeException {

    public OdealClientException(String message) {
        super(message);
    }

    public OdealClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
