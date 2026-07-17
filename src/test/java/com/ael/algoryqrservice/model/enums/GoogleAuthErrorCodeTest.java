package com.ael.algoryqrservice.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAuthErrorCodeTest {

    @Test
    void fromMessage_whenAccountNotFound_thenMapCorrectly() {
        assertThat(GoogleAuthErrorCode.fromMessage(
                "Google hesabı kayıtlı değil. Önce Google ile kayıt olun."
        )).isEqualTo(GoogleAuthErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void fromMessage_whenEmailNotVerified_thenMapCorrectly() {
        assertThat(GoogleAuthErrorCode.fromMessage(
                "Doğrulanmış Google e-posta adresi gerekli"
        )).isEqualTo(GoogleAuthErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    void fromMessage_whenEmailAlreadyExists_thenMapCorrectly() {
        assertThat(GoogleAuthErrorCode.fromMessage(
                "Bu e-posta adresi zaten kayıtlı"
        )).isEqualTo(GoogleAuthErrorCode.ACCOUNT_EXISTS);
    }

    @Test
    void fromMessage_whenSessionMissing_thenMapToOauthFailed() {
        assertThat(GoogleAuthErrorCode.fromMessage(
                "Google kimlik doğrulama oturumu bulunamadı"
        )).isEqualTo(GoogleAuthErrorCode.OAUTH_FAILED);
    }
}
