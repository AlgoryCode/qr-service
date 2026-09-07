package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.access.OnboardingPackageService;
import com.ael.algoryqrservice.model.dto.AccessSessionResponse;
import com.ael.algoryqrservice.model.dto.OnboardingPackageStartRequest;
import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/onboarding/package")
@RequiredArgsConstructor
public class OnboardingPackageController {

    private final OnboardingPackageService onboardingPackageService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public PlanPackageResponse catalog() {
        return onboardingPackageService.catalogPackage();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessSessionResponse start(@Valid @RequestBody(required = false) OnboardingPackageStartRequest request) {
        Long packageId = request == null ? null : request.packageId();
        return onboardingPackageService.start(securityUtils.getCurrentUserId(), packageId);
    }
}
