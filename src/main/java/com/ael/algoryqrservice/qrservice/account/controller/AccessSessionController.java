package com.ael.algoryqrservice.qrservice.account.controller;

import com.ael.algoryqrservice.qrservice.access.AccessSessionMapper;
import com.ael.algoryqrservice.qrservice.access.SessionAccessService;
import com.ael.algoryqrservice.qrservice.account.dto.AccessSessionResponse;
import com.ael.algoryqrservice.qrservice.account.dto.SessionContextResponse;
import com.ael.algoryqrservice.qrservice.account.service.SessionContextService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
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
    private final SecurityUtils securityUtils;

    @GetMapping("/session")
    public AccessSessionResponse session() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        return AccessSessionMapper.toResponse(sessionAccessService.resolve(merchantId));
    }

    @GetMapping("/context")
    public SessionContextResponse context() {
        return sessionContextService.resolve(securityUtils.getCurrentMerchantId());
    }
}
