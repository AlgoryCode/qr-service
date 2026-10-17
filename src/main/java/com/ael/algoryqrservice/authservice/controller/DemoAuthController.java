package com.ael.algoryqrservice.authservice.controller;

import com.ael.algoryqrservice.authservice.application.auth.DemoAccountRegistration;
import com.ael.algoryqrservice.authservice.application.auth.DemoAccountSupport;
import com.ael.algoryqrservice.authservice.application.auth.DemoLoginUseCase;
import com.ael.algoryqrservice.authservice.application.auth.DemoUsernameAvailabilityUseCase;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/demo")
@RequiredArgsConstructor
public class DemoAuthController {

    private final DemoAccountRegistration demoAccountRegistration;
    private final DemoUsernameAvailabilityUseCase usernameAvailabilityUseCase;
    private final DemoLoginUseCase demoLoginUseCase;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody DemoRegisterRequest request) {
        var subject = demoAccountRegistration.save(
                request.username(),
                request.password(),
                request.passwordConfirm()
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", subject.subjectId());
        body.put("username", DemoAccountSupport.normalizeUsername(request.username()));
        body.put("message", "Demo hesap olusturuldu. Giris yaparak devam edin.");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/register")
    public ResponseEntity<Map<String, Object>> registerAvailability(@RequestParam("username") String username) {
        return ResponseEntity.ok(usernameAvailabilityUseCase.check(username));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody DemoLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                demoLoginUseCase.login(request.username(), request.password(), ClientInfo.from(httpRequest))
        );
    }

    public record DemoRegisterRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 8) String password,
            String passwordConfirm
    ) {
    }

    public record DemoLoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }
}
