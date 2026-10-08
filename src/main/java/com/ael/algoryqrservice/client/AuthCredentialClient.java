package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.config.AuthServiceClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.dto.EmailVerificationDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCredentialClient {

    private static final String EMAIL_STATUS = "/internal/credentials/email-verification/status";
    private static final String EMAIL_REQUEST = "/internal/credentials/email-verification/request";
    private static final String EMAIL_RESEND = "/internal/credentials/email-verification/resend";
    private static final String EMAIL_VERIFY = "/internal/credentials/email-verification/verify";
    private static final String EMAIL_SEND = "/internal/credentials/email-verification/send";
    private static final String EMAIL_REISSUE = "/internal/credentials/email-verification/reissue";
    private static final String PASSWORD_REQUEST = "/internal/credentials/password-change/request";
    private static final String PASSWORD_CONFIRM = "/internal/credentials/password-change/confirm";

    private final RestClient.Builder restClientBuilder;
    private final AuthServiceClientProperties properties;
    private final ObjectMapper objectMapper;

    public EmailVerificationDtos.Status emailStatus(Long merchantId) {
        return post(EMAIL_STATUS, new MerchantBody(merchantId), EmailStatusBody.class).toStatus();
    }

    public EmailVerificationDtos.Status requestEmailCode(Long merchantId) {
        return post(EMAIL_REQUEST, new MerchantBody(merchantId), EmailStatusBody.class).toStatus();
    }

    public void resendEmailCode(String email) {
        post(EMAIL_RESEND, new EmailBody(email), Void.class);
    }

    public EmailVerificationDtos.Status verifyEmail(Long merchantId, String code) {
        return post(EMAIL_VERIFY, new VerifyBody(merchantId, null, code), EmailStatusBody.class).toStatus();
    }

    public EmailVerificationDtos.Status verifyEmailByAddress(String email, String code) {
        return post(EMAIL_VERIFY, new VerifyBody(null, email, code), EmailStatusBody.class).toStatus();
    }

    public void sendEmailCode(Long merchantId) {
        post(EMAIL_SEND, new MerchantBody(merchantId), Void.class);
    }

    public void reissueEmailCode(Long merchantId, String email) {
        post(EMAIL_REISSUE, new ReissueBody(merchantId, email), Void.class);
    }

    public AccountDtos.PasswordChangeCodeResponse requestPasswordChange(Long merchantId) {
        PasswordCodeBody body = post(PASSWORD_REQUEST, new MerchantBody(merchantId), PasswordCodeBody.class);
        return AccountDtos.PasswordChangeCodeResponse.builder()
                .maskedEmail(body.maskedEmail())
                .expiresInSeconds(body.expiresInSeconds())
                .validityMinutes(body.validityMinutes())
                .build();
    }

    public void confirmPasswordChange(Long merchantId, String code, String newPassword, String confirmPassword) {
        post(PASSWORD_CONFIRM, new PasswordConfirmBody(merchantId, code, newPassword, confirmPassword), Void.class);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BadRequestException("Auth servisi adresi tanımlı değil");
        }
        try {
            RestClient.ResponseSpec response = restClientBuilder.build()
                    .post()
                    .uri(trimSlash(baseUrl) + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve();
            if (responseType == Void.class) {
                response.toBodilessEntity();
                return null;
            }
            return response.body(responseType);
        } catch (RestClientResponseException exception) {
            String message = readMessage(exception.getResponseBodyAsString(), "Auth servisi isteği başarısız");
            int status = exception.getStatusCode().value();
            log.warn("Auth credential call failed. path={} status={}", path, status);
            if (status == 404) {
                throw new NotFoundException(message);
            }
            throw new BadRequestException(message);
        } catch (RestClientException exception) {
            log.warn("Auth credential call unavailable. path={} reason={}", path, exception.getMessage());
            throw new BadRequestException("Auth servisi kullanılamıyor");
        }
    }

    private String readMessage(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (Exception exception) {
            log.debug("Auth error body was not json");
        }
        return fallback;
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record MerchantBody(Long merchantId) {
    }

    private record EmailBody(String email) {
    }

    private record VerifyBody(Long merchantId, String email, String code) {
    }

    private record ReissueBody(Long merchantId, String email) {
    }

    private record PasswordConfirmBody(Long merchantId, String code, String newPassword, String confirmPassword) {
    }

    private record EmailStatusBody(boolean verified, String email, LocalDateTime expiresAt) {
        private EmailVerificationDtos.Status toStatus() {
            return new EmailVerificationDtos.Status(verified, email, expiresAt);
        }
    }

    private record PasswordCodeBody(String maskedEmail, int expiresInSeconds, int validityMinutes) {
    }
}
