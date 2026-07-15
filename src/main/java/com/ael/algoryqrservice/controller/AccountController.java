package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.service.AccountService;
import com.ael.algoryqrservice.service.EmailChangeService;
import com.ael.algoryqrservice.service.PasswordChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final EmailChangeService emailChangeService;
    private final PasswordChangeService passwordChangeService;

    @GetMapping("/myprofile")
    public ResponseEntity<AccountDtos.MyProfileResponse> getMyProfile() {
        return ResponseEntity.ok(accountService.getMyProfile());
    }

    @PatchMapping("/myprofile")
    public ResponseEntity<AccountDtos.MyProfileResponse> updateMyProfile(
            @RequestBody AccountDtos.MyProfilePatchRequest request
    ) {
        return ResponseEntity.ok(accountService.updateMyProfile(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AccountDtos.ChangePasswordRequest request) {
        accountService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-change/request-current-code")
    public ResponseEntity<AccountDtos.EmailChangeCodeResponse> requestCurrentEmailCode() {
        return ResponseEntity.ok(emailChangeService.requestCurrentCode());
    }

    @PostMapping("/email-change/verify-current")
    public ResponseEntity<AccountDtos.EmailChangeCodeResponse> verifyCurrentEmail(
            @Valid @RequestBody AccountDtos.EmailChangeVerifyCurrentRequest request
    ) {
        return ResponseEntity.ok(emailChangeService.verifyCurrent(request));
    }

    @PostMapping("/email-change/request-new-code")
    public ResponseEntity<AccountDtos.EmailChangeCodeResponse> requestNewEmailCode(
            @Valid @RequestBody AccountDtos.EmailChangeRequestNewCodeRequest request
    ) {
        return ResponseEntity.ok(emailChangeService.requestNewCode(request));
    }

    @PostMapping("/email-change/confirm")
    public ResponseEntity<Void> confirmEmailChange(
            @Valid @RequestBody AccountDtos.EmailChangeConfirmRequest request
    ) {
        emailChangeService.confirm(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-change/request-code")
    public ResponseEntity<AccountDtos.PasswordChangeCodeResponse> requestPasswordChangeCode() {
        return ResponseEntity.ok(passwordChangeService.requestCode());
    }

    @PostMapping("/password-change/confirm")
    public ResponseEntity<Void> confirmPasswordChange(
            @Valid @RequestBody AccountDtos.ConfirmPasswordChangeRequest request
    ) {
        passwordChangeService.confirm(request);
        return ResponseEntity.noContent().build();
    }
}
