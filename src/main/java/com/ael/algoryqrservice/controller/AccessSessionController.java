package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.access.AccessSessionMapper;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.model.dto.AccessSessionResponse;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/access")
@RequiredArgsConstructor
public class AccessSessionController {

    private final SessionAccessService sessionAccessService;
    private final SecurityUtils securityUtils;

    @GetMapping("/session")
    public AccessSessionResponse session() {
        return AccessSessionMapper.toResponse(sessionAccessService.resolve(securityUtils.getCurrentUserId()));
    }
}
