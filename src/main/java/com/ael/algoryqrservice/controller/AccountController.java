package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.service.AccountService;
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
}
