package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.service.NotificationPublisherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalVerificationMailController {

    private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    private static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";

    private final NotificationPublisherService notificationPublisherService;

    @PostMapping("/verification-codes")
    public ResponseEntity<Void> publish(@Valid @RequestBody VerificationMailRequest request) {
        String userName = request.userName() == null || request.userName().isBlank() ? "Kullanıcı" : request.userName();
        if (EMAIL_VERIFICATION.equals(request.kind())) {
            notificationPublisherService.publishEmailVerificationCode(
                    request.email(),
                    userName,
                    request.code(),
                    request.validityMinutes()
            );
            return ResponseEntity.noContent().build();
        }
        if (PASSWORD_CHANGE.equals(request.kind())) {
            notificationPublisherService.publishPasswordChangeCode(
                    request.email(),
                    userName,
                    request.code(),
                    request.validityMinutes()
            );
            return ResponseEntity.noContent().build();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported verification mail");
    }

    public record VerificationMailRequest(
            @NotBlank String kind,
            @NotBlank @Email String email,
            String userName,
            @NotBlank String code,
            @Min(1) int validityMinutes
    ) {
    }
}
