package com.ael.algoryqrservice.integration.yemeksepeti.client;

public class YemekSepetiClientException extends RuntimeException {

    private final Integer httpStatus;

    public YemekSepetiClientException(String message) {
        this(message, null, null);
    }

    public YemekSepetiClientException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public YemekSepetiClientException(String message, Throwable cause, Integer httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
