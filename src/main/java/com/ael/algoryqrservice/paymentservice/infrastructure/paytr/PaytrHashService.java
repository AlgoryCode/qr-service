package com.ael.algoryqrservice.paymentservice.infrastructure.paytr;

import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaytrProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class PaytrHashService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final PaytrProperties paytrProperties;

    public String tokenHash(String hashStr) {
        return hmacBase64(hashStr + paytrProperties.merchantSalt());
    }

    public String notificationHash(String merchantOid, String status, String totalAmount) {
        return hmacBase64(merchantOid + paytrProperties.merchantSalt() + status + totalAmount);
    }

    public String refundHash(String merchantOid, String returnAmount) {
        return hmacBase64(
                paytrProperties.merchantId() + merchantOid + returnAmount + paytrProperties.merchantSalt()
        );
    }

    public String statusQueryHash(String merchantOid) {
        return hmacBase64(paytrProperties.merchantId() + merchantOid + paytrProperties.merchantSalt());
    }

    public String cardListHash(String utoken) {
        // PayTR CAPI LIST hash is calculated from utoken + merchant_salt.
        // merchant_id is sent as a form field but is not part of this hash.
        return hmacBase64(utoken + paytrProperties.merchantSalt());
    }

    public String cardDeleteHash(String ctoken, String utoken) {
        return hmacBase64(ctoken + utoken + paytrProperties.merchantSalt());
    }

    public boolean matchesNotification(String merchantOid, String status, String totalAmount, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        String actual = notificationHash(merchantOid, status, totalAmount);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hmacBase64(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(
                    paytrProperties.merchantKey().getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            ));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("PayTR hash hesaplanamadi", exception);
        }
    }
}
