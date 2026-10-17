package com.ael.algoryqrservice.authservice.controller;

import com.ael.algoryqrservice.authservice.application.auth.LoginUseCase;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/staff")
@RequiredArgsConstructor
public class StaffAuthController {

    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody StaffLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                loginUseCase.loginStaff(
                        request.username(),
                        request.password(),
                        request.merchantId(),
                        ClientInfo.from(httpRequest)
                )
        );
    }

    public record StaffLoginRequest(
            @NotNull Long merchantId,
            @NotBlank String username,
            @NotBlank String password
    ) {
    }
}
