package com.ael.algoryqrservice.print.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class PrintAgentTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateDeviceToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "pad_" + HexFormat.of().formatHex(bytes);
    }

    public String generatePairingCode() {
        int value = RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    public String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
