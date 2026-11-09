package com.ael.algoryqrservice.qrservice.qr.service;

import com.ael.algoryqrservice.qrservice.qr.repository.QrRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Issues the permanent code in a QR's printed address, e.g. https://domain/qr/k7m2xq9fah. */
@Component
public class QrPublicCodes {

    // Leaves out 0/o and 1/l/i so a code stays readable when typed from a printout.
    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final int CODE_LENGTH = 10;
    private static final int MAX_ATTEMPTS = 8;

    private final SecureRandom secureRandom;
    private final QrRepository qrRepository;
    private final String publicBaseUrl;

    public QrPublicCodes(
            SecureRandom secureRandom,
            QrRepository qrRepository,
            @Value("${app.qr.public-base-url}") String publicBaseUrl
    ) {
        this.secureRandom = secureRandom;
        this.qrRepository = qrRepository;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = generate();
            if (!qrRepository.existsByPublicCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("QR public code could not be generated");
    }

    public String urlFor(String code) {
        return code == null ? null : publicBaseUrl + "/qr/" + code;
    }

    public static boolean isWellFormed(String code) {
        return code != null
                && code.length() == CODE_LENGTH
                && code.chars().allMatch(ch -> ALPHABET.indexOf(ch) >= 0);
    }

    private String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
