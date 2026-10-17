package com.ael.algoryqrservice.authservice.infrastructure.client;

public record PackageState(String status, String packageCode, String periodEnd) {

    public static PackageState none() {
        return new PackageState("NONE", null, null);
    }

    public boolean inactive() {
        return "INACTIVE".equals(status);
    }

    public boolean missing() {
        return status == null || "NONE".equals(status);
    }
}
