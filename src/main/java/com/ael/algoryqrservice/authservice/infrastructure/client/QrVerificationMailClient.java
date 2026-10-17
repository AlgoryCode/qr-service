package com.ael.algoryqrservice.authservice.infrastructure.client;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.platform.service.NotificationPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QrVerificationMailClient {

    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";

    private final NotificationPublisherService notificationPublisherService;

    public void send(String kind, String email, String userName, String code, int validityMinutes) {
        String resolvedName = userName == null || userName.isBlank() ? "Kullanıcı" : userName;
        if (EMAIL_VERIFICATION.equals(kind)) {
            notificationPublisherService.publishEmailVerificationCode(email, resolvedName, code, validityMinutes);
            return;
        }
        if (PASSWORD_CHANGE.equals(kind)) {
            notificationPublisherService.publishPasswordChangeCode(email, resolvedName, code, validityMinutes);
            return;
        }
        throw new BadRequestException("Doğrulama kodu gönderilemedi. Lütfen daha sonra tekrar deneyin.");
    }

    public record VerificationMail(String kind, String email, String userName, String code, int validityMinutes) {
    }
}
