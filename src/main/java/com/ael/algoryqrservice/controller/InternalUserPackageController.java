package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.service.UserPackageAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/internal/merchants", "/internal/users"})
@RequiredArgsConstructor
public class InternalUserPackageController {

    private final UserPackageAssignmentService userPackageAssignmentService;

    @GetMapping("/{merchantId}/package")
    public UserPackageAssignmentService.PackageControl read(@PathVariable Long merchantId) {
        return userPackageAssignmentService.read(merchantId);
    }

    @PostMapping("/{merchantId}/package")
    public ResponseEntity<Void> create(
            @PathVariable Long merchantId,
            @Valid @RequestBody CreateUserPackageRequest request
    ) {
        userPackageAssignmentService.create(merchantId, request.packageId(), request.packageCode());
        return ResponseEntity.noContent().build();
    }

    public record CreateUserPackageRequest(
            Long packageId,
            String packageCode
    ) {
    }
}
