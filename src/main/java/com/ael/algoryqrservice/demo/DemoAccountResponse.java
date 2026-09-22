package com.ael.algoryqrservice.demo;

public record DemoAccountResponse(boolean enabled, String email, String password) {

    public static DemoAccountResponse disabled() {
        return new DemoAccountResponse(false, null, null);
    }

    public static DemoAccountResponse open(String email, String password) {
        return new DemoAccountResponse(true, email, password);
    }
}
