package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.access.OnboardingPackageService;
import com.ael.algoryqrservice.model.dto.DemoSetupRequest;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoSetupController {

    private final OnboardingPackageService onboardingPackageService;
    private final SecurityUtils securityUtils;

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setup(@Valid @RequestBody DemoSetupRequest request) {
        onboardingPackageService.completeDemoSetup(
                securityUtils.getCurrentUserId(),
                request.businessType(),
                request.usagePurpose()
        );
    }
}
