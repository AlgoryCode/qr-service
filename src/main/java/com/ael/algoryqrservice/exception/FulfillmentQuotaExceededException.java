package com.ael.algoryqrservice.exception;

public class FulfillmentQuotaExceededException extends RuntimeException {

    public FulfillmentQuotaExceededException() {
        super("Kota yetersiz");
    }
}
