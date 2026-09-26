package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy path used by dashboard BFF ({@code /api/auth/session}). Same data as JWT claims, resolved live.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthAccessProfileController {

    private final AuthService authService;

    @GetMapping("/access-profile")
    public UserAccessProfile accessProfile() {
        return authService.getAccessProfile();
    }
}
