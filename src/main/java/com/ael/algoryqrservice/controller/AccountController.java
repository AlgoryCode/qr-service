package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.dto.AccountOverviewDtos;
import com.ael.algoryqrservice.model.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.model.dto.EmailVerificationDtos;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.SubscriptionOverviewResponse;
import com.ael.algoryqrservice.service.AccountCredentialGateway;
import com.ael.algoryqrservice.service.AccountFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountFacadeService accountFacade;
    private final AccountCredentialGateway accountCredentialGateway;

    @GetMapping("/email-verification/status")
    public ResponseEntity<EmailVerificationDtos.Status> emailVerificationStatus() {
        return ResponseEntity.ok(accountCredentialGateway.emailStatus());
    }

    @PostMapping("/email-verification/request-code")
    public ResponseEntity<EmailVerificationDtos.Status> requestEmailVerificationCode() {
        return ResponseEntity.ok(accountCredentialGateway.requestEmailCode());
    }

    @PostMapping("/email-verification/verify")
    public ResponseEntity<EmailVerificationDtos.Status> verifyEmail(
            @Valid @RequestBody EmailVerificationDtos.VerifyRequest request
    ) {
        return ResponseEntity.ok(accountCredentialGateway.verifyEmail(request));
    }

    @PostMapping("/password-change/request-code")
    public ResponseEntity<AccountDtos.PasswordChangeCodeResponse> requestPasswordChangeCode() {
        return ResponseEntity.ok(accountCredentialGateway.requestPasswordChange());
    }

    @PostMapping("/password-change/confirm")
    public ResponseEntity<Void> confirmPasswordChange(
            @Valid @RequestBody AccountDtos.ConfirmPasswordChangeRequest request
    ) {
        accountCredentialGateway.confirmPasswordChange(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/myprofile")
    public AccountDtos.MyProfileResponse myProfile() {
        return accountFacade.getMyProfile();
    }

    @PatchMapping("/myprofile")
    public AccountDtos.MyProfileResponse updateMyProfile(@Valid @RequestBody AccountDtos.MyProfilePatchRequest request) {
        return accountFacade.updateMyProfile(request);
    }

    @GetMapping("/billing-addresses")
    public BillingAddressPageResponse billingAddresses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return accountFacade.listBillingAddresses(page, size);
    }

    @GetMapping("/purchases")
    public List<PurchaseResponse> purchases() {
        return accountFacade.listPurchases();
    }

    @GetMapping("/subscription-overview")
    public SubscriptionOverviewResponse subscriptionOverview() {
        return accountFacade.subscriptionOverview();
    }

    @GetMapping("/overview")
    public ResponseEntity<AccountOverviewDtos.OverviewResponse> overview() {
        AccountOverviewDtos.OverviewResponse body = accountFacade.getOverview();
        if (body.getProfile() != null && !body.getProfile().isOk()) {
            int status = body.getProfile().getStatus() != null ? body.getProfile().getStatus() : 502;
            if (status == 401) {
                return ResponseEntity.status(401).body(body);
            }
            if (status >= 500) {
                return ResponseEntity.status(502).body(body);
            }
            return ResponseEntity.status(status).body(body);
        }
        return ResponseEntity.ok(body);
    }
}
