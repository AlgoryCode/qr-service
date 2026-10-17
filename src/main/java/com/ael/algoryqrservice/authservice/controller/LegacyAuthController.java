package com.ael.algoryqrservice.authservice.controller;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.application.auth.RoleResponseFactory;
import com.ael.algoryqrservice.authservice.application.auth.DemoUsernameAvailabilityUseCase;
import com.ael.algoryqrservice.authservice.application.auth.LoginUseCase;
import com.ael.algoryqrservice.authservice.application.auth.MerchantPackageGate;
import com.ael.algoryqrservice.authservice.application.auth.RegisterAccountUseCase;
import com.ael.algoryqrservice.authservice.application.auth.SubjectLoader;
import com.ael.algoryqrservice.authservice.application.auth.TokenUseCase;
import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.security.AuthPrincipal;
import com.ael.algoryqrservice.authservice.service.GoogleAuthHandoffService;
import com.ael.algoryqrservice.authservice.service.GoogleAuthSessionService;
import com.ael.algoryqrservice.authservice.application.auth.GoogleAuthIntent;
import com.ael.algoryqrservice.authservice.application.auth.GoogleSubjectResolver;
import com.ael.algoryqrservice.authservice.security.GoogleOAuthPaths;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LegacyAuthController {

    private static final Logger log = LoggerFactory.getLogger(LegacyAuthController.class);

    private final LoginUseCase loginUseCase;
    private final RegisterAccountUseCase registerAccountUseCase;
    private final DemoUsernameAvailabilityUseCase usernameAvailabilityUseCase;
    private final TokenUseCase tokenUseCase;
    private final MerchantPackageGate merchantPackageGate;
    private final SubjectLoader subjectLoader;
    private final RoleResponseFactory roleResponseFactory;
    private final GoogleAuthSessionService sessionService;
    private final GoogleAuthHandoffService handoffService;
    private final GoogleSubjectResolver subjectResolver;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LegacyLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String identifier = request.resolvedIdentifier();
        log.info("Legacy auth login identifier={} clientIp={}", identifier, ClientInfo.from(httpRequest).ipAddress());
        return ResponseEntity.ok(
                loginUseCase.login(identifier, request.password(), request.merchantId(), ClientInfo.from(httpRequest))
        );
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody LegacyRegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Legacy auth register email={} clientIp={}", request.resolvedEmail(), ClientInfo.from(httpRequest).ipAddress());
        return ResponseEntity.ok(
                registerAccountUseCase.register(
                        request.resolvedEmail(),
                        request.password(),
                        request.firstName(),
                        request.lastName(),
                        request.phone(),
                        ClientInfo.from(httpRequest)
                )
        );
    }

    @GetMapping("/register")
    public ResponseEntity<Map<String, Object>> registerAvailability(@RequestParam("username") String username) {
        return ResponseEntity.ok(usernameAvailabilityUseCase.check(username));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(tokenUseCase.refresh(request.refreshToken(), ClientInfo.from(httpRequest)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Legacy auth logout clientIp={}", ClientInfo.from(httpRequest).ipAddress());
        tokenUseCase.expire(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        AuthSubject subject = subjectLoader.load(principal.subjectType(), principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Subject not found"));
        merchantPackageGate.assertOpen(subject);
        return ResponseEntity.ok(tokenUseCase.create(subject, ClientInfo.from(httpRequest)));
    }

    @GetMapping("/access-profile")
    public ResponseEntity<Map<String, Object>> accessProfile(@AuthenticationPrincipal AuthPrincipal principal) {
        AuthSubject subject = subjectLoader.load(principal.subjectType(), principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Subject not found"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", subject.roleCode());
        body.put("scopes", subject.scopes() == null ? List.of() : subject.scopes());
        body.put("profile", roleResponseFactory.profile(subject));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/google/authorize")
    public void googleAuthorize(
            @RequestParam String intent,
            @RequestParam(required = false) String audience,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        GoogleAuthIntent parsed = GoogleAuthIntent.from(intent);
        String resolvedAudience = audience == null || audience.isBlank()
                ? AuthSubject.TYPE_MERCHANT
                : audience.trim().toUpperCase(Locale.ROOT);
        subjectResolver.require(resolvedAudience);
        sessionService.store(request, response, parsed, resolvedAudience);
        response.sendRedirect(GoogleOAuthPaths.AUTHORIZATION);
    }

    @PostMapping("/google/redeem")
    public ResponseEntity<Map<String, Object>> googleRedeem(
            @Valid @RequestBody GoogleRedeemRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Legacy Google OAuth handoff redeem requested");
        return ResponseEntity.ok(handoffService.redeem(request.ticket(), ClientInfo.from(httpRequest)));
    }

    public record GoogleRedeemRequest(@NotBlank String ticket) {
    }

    public record LegacyLoginRequest(
            String identifier,
            String email,
            @NotBlank String password,
            Long merchantId
    ) {
        String resolvedIdentifier() {
            if (StringUtils.hasText(identifier)) {
                return identifier.trim();
            }
            if (StringUtils.hasText(email)) {
                return email.trim();
            }
            throw new BadRequestException("identifier or email is required");
        }
    }

    public record LegacyRegisterRequest(
            @Email String email,
            @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 8) String password,
            String passwordConfirm,
            String firstName,
            String lastName,
            String phone
    ) {
        String resolvedEmail() {
            if (StringUtils.hasText(email)) {
                return email.trim();
            }
            if (StringUtils.hasText(username)) {
                return username.trim();
            }
            throw new BadRequestException("email or username is required");
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
