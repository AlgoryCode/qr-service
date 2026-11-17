package com.ael.algoryqrservice.qrservice.exception;

public class FulfillmentQuotaExceededException extends RuntimeException {

    private final Long limit;
    private final Long used;

    public FulfillmentQuotaExceededException() {
        this(null, null);
    }

    public FulfillmentQuotaExceededException(Long limit, Long used) {
        super("Kota yetersiz");
        this.limit = limit;
        this.used = used;
    }

    public Long limit() {
        return limit;
    }

    public Long used() {
        return used;
    }
}
