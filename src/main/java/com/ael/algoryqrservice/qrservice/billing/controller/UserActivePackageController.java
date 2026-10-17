package com.ael.algoryqrservice.qrservice.billing.controller;

import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.account.dto.SessionContextResponse;
import com.ael.algoryqrservice.qrservice.account.service.SessionContextService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserActivePackageController {

    private final SessionContextService sessionContextService;
    private final SecurityUtils securityUtils;

    @GetMapping("/{merchantId}/package")
    public SessionContextResponse findPackage(@PathVariable Long merchantId) {
        Long accountMerchantId = securityUtils.getCurrentMerchantId();
        if (!merchantId.equals(accountMerchantId) && !securityUtils.matchesTokenUser(merchantId)) {
            throw new ForbiddenException("Aktif paket bu işletmeye ait değil");
        }
        return sessionContextService.resolve(accountMerchantId);
    }
}
