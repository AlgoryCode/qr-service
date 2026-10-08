package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.model.dto.SessionContextResponse;
import com.ael.algoryqrservice.service.SessionContextService;
import com.ael.algoryqrservice.util.SecurityUtils;
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
