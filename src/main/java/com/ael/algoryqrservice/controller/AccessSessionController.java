package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.access.AccessSessionMapper;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.model.dto.AccessSessionResponse;
import com.ael.algoryqrservice.model.dto.SessionContextResponse;
import com.ael.algoryqrservice.service.ExternalPackageViewService;
import com.ael.algoryqrservice.service.SessionContextService;
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
    private final SessionContextService sessionContextService;
    private final ExternalPackageViewService externalPackageView;
    private final SecurityUtils securityUtils;

    @GetMapping("/session")
    public AccessSessionResponse session() {
        Long userId = securityUtils.getCurrentUserId();
        return externalPackageView.session(userId)
                .map(AccessSessionMapper::toResponse)
                .orElseGet(() -> AccessSessionMapper.toResponse(sessionAccessService.resolve(userId)));
    }

    @GetMapping("/context")
    public SessionContextResponse context() {
        return sessionContextService.resolve(securityUtils.getCurrentUserId());
    }
}
