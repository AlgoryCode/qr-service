package com.ael.algoryqrservice.fulfillmentservice.domain.exception;

public class QuotaExceededException extends DomainException {

    private final String featureCode;
    private final Long limit;
    private final Long used;

    public QuotaExceededException(String message) {
        this(message, null, null, null);
    }

    public QuotaExceededException(String message, String featureCode, Long limit, Long used) {
        super("QUOTA_EXCEEDED", message);
        this.featureCode = featureCode;
        this.limit = limit;
        this.used = used;
    }

    public String featureCode() {
        return featureCode;
    }

    public Long limit() {
        return limit;
    }

    public Long used() {
        return used;
    }
}
