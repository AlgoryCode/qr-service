package com.ael.algoryqrservice.qrservice.exception;

public class FulfillmentQuotaExceededException extends RuntimeException {

    public FulfillmentQuotaExceededException() {
        super("Kota yetersiz");
    }
}
