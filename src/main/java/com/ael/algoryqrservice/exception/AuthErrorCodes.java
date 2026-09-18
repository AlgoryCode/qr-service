package com.ael.algoryqrservice.exception;

public final class AuthErrorCodes {

    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "E-posta adresiniz onaylanmamış. Lütfen e-postanıza gelen kodu doğrulayın.";
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    public static final String TOO_MANY_REQUESTS_MESSAGE =
            "Çok fazla deneme yapıldı. Lütfen bir süre sonra tekrar deneyin.";

    private AuthErrorCodes() {
    }
}
