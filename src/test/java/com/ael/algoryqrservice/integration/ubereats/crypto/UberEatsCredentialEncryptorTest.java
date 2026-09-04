package com.ael.algoryqrservice.integration.ubereats.crypto;

import com.ael.algoryqrservice.integration.ubereats.config.UberEatsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UberEatsCredentialEncryptorTest {

    private UberEatsCredentialEncryptor encryptor;

    @BeforeEach
    void setUp() {
        UberEatsProperties properties = new UberEatsProperties();
        properties.setEncryptKey("dGVzdC10Z28tZW5jcnlwdC1rZXktMzItYnl0ZXMh");
        encryptor = new UberEatsCredentialEncryptor(properties);
    }

    @Test
    void encrypt_whenPlaintext_thenRoundTrip() {
        String cipher = encryptor.encrypt("secret-key");
        assertThat(cipher).isNotBlank().isNotEqualTo("secret-key");
        assertThat(encryptor.decrypt(cipher)).isEqualTo("secret-key");
    }

    @Test
    void mask_whenLongValue_thenKeepLastFour() {
        assertThat(encryptor.mask("abcdef1234")).isEqualTo("****1234");
    }
}
