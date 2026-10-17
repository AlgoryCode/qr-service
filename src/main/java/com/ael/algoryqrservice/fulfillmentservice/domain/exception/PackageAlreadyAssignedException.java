package com.ael.algoryqrservice.fulfillmentservice.domain.exception;

public class PackageAlreadyAssignedException extends DomainException {

    public PackageAlreadyAssignedException() {
        super("PACKAGE_EXISTS", "Aktif paket mevcut");
    }
}
