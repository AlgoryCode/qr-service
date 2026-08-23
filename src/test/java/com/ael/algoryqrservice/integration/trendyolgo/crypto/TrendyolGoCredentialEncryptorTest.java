package com.ael.algoryqrservice.integration.trendyolgo.crypto;

import com.ael.algoryqrservice.integration.trendyolgo.config.TrendyolGoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrendyolGoCredentialEncryptorTest {

    private TrendyolGoCredentialEncryptor encryptor;

    @BeforeEach
    void setUp() {
        TrendyolGoProperties properties = new TrendyolGoProperties();
        properties.setEncryptKey("dGVzdC10Z28tZW5jcnlwdC1rZXktMzItYnl0ZXMh");
        encryptor = new TrendyolGoCredentialEncryptor(properties);
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
