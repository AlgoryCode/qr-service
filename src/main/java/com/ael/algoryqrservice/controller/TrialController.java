package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.TrialDtos;
import com.ael.algoryqrservice.service.TrialService;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trials")
@RequiredArgsConstructor
public class TrialController {
    private final TrialService service;
    private final SecurityUtils securityUtils;

    @PostMapping("/digital-menu-pro")
    @ResponseStatus(HttpStatus.CREATED)
    public TrialDtos.Status start() {
        return service.startDigitalMenuPro(securityUtils.getCurrentUser().getId());
    }

    @GetMapping("/digital-menu-pro/status")
    public TrialDtos.Status status() {
        return service.status(securityUtils.getCurrentUser().getId());
    }
}
