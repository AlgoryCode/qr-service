package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.PlanPackageResponse;
import com.ael.algoryqrservice.model.dto.TrialDtos;
import com.ael.algoryqrservice.model.dto.TrialStartRequest;
import com.ael.algoryqrservice.service.TrialService;
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

import java.util.List;

@RestController
@RequestMapping("/trials")
@RequiredArgsConstructor
public class TrialController {

    private final TrialService service;
    private final SecurityUtils securityUtils;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrialDtos.Status start(@Valid @RequestBody TrialStartRequest request) {
        return service.start(securityUtils.getCurrentUser().getId(), request.getPackageId());
    }

    @GetMapping("/status")
    public TrialDtos.Status status() {
        return service.status(securityUtils.getCurrentUser().getId());
    }

    @GetMapping("/eligible-packages")
    public List<PlanPackageResponse> eligiblePackages() {
        return service.listEligiblePackages();
    }

    @PostMapping("/digital-menu-pro")
    @ResponseStatus(HttpStatus.CREATED)
    public TrialDtos.Status startLegacy() {
        return service.startDigitalMenuPro(securityUtils.getCurrentUser().getId());
    }

    @GetMapping("/digital-menu-pro/status")
    public TrialDtos.Status statusLegacy() {
        return service.status(securityUtils.getCurrentUser().getId());
    }
}
