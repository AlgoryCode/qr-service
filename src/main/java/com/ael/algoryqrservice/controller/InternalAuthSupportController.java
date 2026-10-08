package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.service.UserAccessProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthSupportController {

    private final UserAccessProfileService userAccessProfileService;

    @GetMapping("/users/{userId}/access-profile")
    public ResponseEntity<UserAccessProfile> accessProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userAccessProfileService.resolve(userId));
    }
}
