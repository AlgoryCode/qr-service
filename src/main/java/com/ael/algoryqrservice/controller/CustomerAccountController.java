package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.CustomerAuthDtos;
import com.ael.algoryqrservice.service.CustomerAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer/account")
@RequiredArgsConstructor
public class CustomerAccountController {

    private final CustomerAccountService customerAccountService;

    @GetMapping("/profile")
    public ResponseEntity<CustomerAuthDtos.CustomerProfileResponse> getMyProfile() {
        return ResponseEntity.ok(customerAccountService.getMyProfile());
    }

    @PatchMapping("/profile")
    public ResponseEntity<CustomerAuthDtos.CustomerProfileResponse> updateMyProfile(
            @RequestBody CustomerAuthDtos.CustomerProfilePatchRequest request
    ) {
        return ResponseEntity.ok(customerAccountService.updateMyProfile(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody CustomerAuthDtos.CustomerChangePasswordRequest request
    ) {
        customerAccountService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/memberships/join")
    public ResponseEntity<CustomerAuthDtos.MembershipResponse> joinMembership(
            @Valid @RequestBody CustomerAuthDtos.JoinMembershipRequest request
    ) {
        return ResponseEntity.ok(customerAccountService.joinMembership(request.getMenuId()));
    }

    @GetMapping("/memberships/{menuId}")
    public ResponseEntity<CustomerAuthDtos.MembershipResponse> getMembership(@PathVariable Long menuId) {
        return ResponseEntity.ok(customerAccountService.getMembership(menuId));
    }
}
