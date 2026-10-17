package com.ael.algoryqrservice.authservice.controller;

import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.application.auth.RoleResponseFactory;
import com.ael.algoryqrservice.authservice.application.auth.DemoUsernameAvailabilityUseCase;
import com.ael.algoryqrservice.authservice.application.auth.LoginUseCase;
import com.ael.algoryqrservice.authservice.application.auth.MerchantPackageGate;
import com.ael.algoryqrservice.authservice.application.auth.RegisterAccountUseCase;
import com.ael.algoryqrservice.authservice.application.auth.SubjectLoader;
import com.ael.algoryqrservice.authservice.application.auth.TokenUseCase;
import com.ael.algoryqrservice.authservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.authservice.security.AuthPrincipal;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final LoginUseCase loginUseCase;
    private final RegisterAccountUseCase registerAccountUseCase;
    private final DemoUsernameAvailabilityUseCase usernameAvailabilityUseCase;
    private final TokenUseCase tokenUseCase;
    private final MerchantPackageGate merchantPackageGate;
    private final SubjectLoader subjectLoader;
    private final RoleResponseFactory roleResponseFactory;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                loginUseCase.login(
                        request.identifier(),
                        request.password(),
                        request.merchantId(),
                        ClientInfo.from(httpRequest)
                )
        );
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                registerAccountUseCase.register(
                        request.email(),
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

    @PostMapping("/tokens")
    public ResponseEntity<Map<String, Object>> createToken(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        AuthSubject subject = subjectLoader.load(principal.subjectType(), principal.merchantId())
                .orElseThrow(() -> new UnauthorizedException("Subject not found"));
        merchantPackageGate.assertOpen(subject);
        return ResponseEntity.ok(tokenUseCase.create(subject, ClientInfo.from(httpRequest)));
    }

    @PostMapping("/tokens/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(tokenUseCase.refresh(request.refreshToken(), ClientInfo.from(httpRequest)));
    }

    @PostMapping("/tokens/expire")
    public ResponseEntity<Void> expire(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Logout (expire token) requested clientIp={}", ClientInfo.from(httpRequest).ipAddress());
        tokenUseCase.expire(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tokens/revoke")
    public ResponseEntity<Void> revoke(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Logout (revoke token) requested clientIp={}", ClientInfo.from(httpRequest).ipAddress());
        tokenUseCase.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/access")
    public ResponseEntity<Map<String, Object>> access(@AuthenticationPrincipal AuthPrincipal principal) {
        AuthSubject subject = subjectLoader.load(principal.subjectType(), principal.merchantId())
                .orElseThrow(() -> new UnauthorizedException("Subject not found"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", subject.roleCode());
        body.put("scopes", subject.scopes() == null ? List.of() : subject.scopes());
        body.put("profile", roleResponseFactory.profile(subject));
        return ResponseEntity.ok(body);
    }

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank String password,
            Long merchantId
    ) {
    }

    public record RegisterRequest(
            @Email String email,
            @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 8) String password,
            String passwordConfirm,
            String firstName,
            String lastName,
            String phone
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
