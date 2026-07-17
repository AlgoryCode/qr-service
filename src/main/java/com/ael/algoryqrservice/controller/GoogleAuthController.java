package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.dto.GoogleAuthRedeemRequest;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.security.GoogleOAuthPaths;
import com.ael.algoryqrservice.service.GoogleAuthHandoffService;
import com.ael.algoryqrservice.service.GoogleAuthSessionService;
import com.ael.algoryqrservice.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/google-auth")
@RequiredArgsConstructor
public class GoogleAuthController {

    private final GoogleAuthSessionService authSessionService;
    private final GoogleAuthHandoffService handoffService;

    @GetMapping("/authorize")
    public RedirectView authorize(
            @RequestParam String intent,
            HttpServletRequest request
    ) {
        authSessionService.storeIntent(request, GoogleAuthIntent.from(intent));
        RedirectView redirectView = new RedirectView(GoogleOAuthPaths.AUTHORIZATION);
        redirectView.setContextRelative(false);
        redirectView.setPropagateQueryParams(false);
        return redirectView;
    }

    @PostMapping("/redeem")
    public ResponseEntity<AuthResponse> redeem(
            @Valid @RequestBody GoogleAuthRedeemRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(
                handoffService.redeem(request.ticket(), ClientInfo.from(servletRequest))
        );
    }
}
