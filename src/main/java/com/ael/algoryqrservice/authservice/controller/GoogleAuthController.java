package com.ael.algoryqrservice.authservice.controller;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.service.GoogleAuthHandoffService;
import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthIntent;
import com.ael.algoryqrservice.authservice.service.GoogleAuthSessionService;
import com.ael.algoryqrservice.authservice.application.auth.GoogleLoginUseCase;
import com.ael.algoryqrservice.authservice.application.auth.GoogleSubjectResolver;
import com.ael.algoryqrservice.authservice.security.GoogleOAuthPaths;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/google")
@RequiredArgsConstructor
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    private final GoogleAuthSessionService sessionService;
    private final GoogleSubjectResolver subjectResolver;
    private final GoogleAuthHandoffService handoffService;
    private final GoogleLoginUseCase googleLoginUseCase;

    @GetMapping("/authorize")
    public void authorize(
            @RequestParam String intent,
            @RequestParam(required = false) String audience,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        GoogleAuthIntent parsed = GoogleAuthIntent.from(intent);
        String resolvedAudience = resolveAudience(audience);
        subjectResolver.require(resolvedAudience);
        sessionService.store(request, response, parsed, resolvedAudience);
        log.info("Redirecting browser to Google OAuth authorization intent={} audience={}", parsed.value(), resolvedAudience);
        response.sendRedirect(GoogleOAuthPaths.AUTHORIZATION);
    }

    @PostMapping("/redeem")
    public ResponseEntity<Map<String, Object>> redeem(
            @Valid @RequestBody RedeemRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Google OAuth handoff redeem requested");
        return ResponseEntity.ok(handoffService.redeem(request.ticket(), ClientInfo.from(httpRequest)));
    }

    @PostMapping("/id-token")
    public ResponseEntity<Map<String, Object>> idToken(
            @Valid @RequestBody IdTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(googleLoginUseCase.authenticate(
                request.idToken(),
                request.intent(),
                resolveAudience(request.audience()),
                ClientInfo.from(httpRequest)
        ));
    }

    private String resolveAudience(String audience) {
        if (audience == null || audience.isBlank()) {
            return AuthSubject.TYPE_MERCHANT;
        }
        return audience.trim().toUpperCase(Locale.ROOT);
    }

    public record RedeemRequest(@NotBlank String ticket) {
    }

    public record IdTokenRequest(
            @NotBlank String idToken,
            @NotBlank String intent,
            String audience
    ) {
    }
}
