package com.ael.algoryqrservice.staffservice.internal.controller;

import com.ael.algoryqrservice.staffservice.application.UpsertStaffUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/internal/staff")
@RequiredArgsConstructor
public class InternalStaffController {

    private final UpsertStaffUseCase upsertStaffUseCase;

    @PutMapping("/{staffId}")
    public ResponseEntity<Void> upsert(
            @PathVariable Long staffId,
            @Valid @RequestBody UpsertRequest request
    ) {
        upsertStaffUseCase.upsert(staffId, new UpsertStaffUseCase.Command(
                request.merchantId(),
                request.role(),
                request.branchId(),
                request.username(),
                request.passwordHash(),
                request.displayName(),
                request.active(),
                request.commissionEnabled(),
                request.commissionType(),
                request.commissionValue()
        ));
        return ResponseEntity.noContent().build();
    }

    public record UpsertRequest(
            @NotNull Long merchantId,
            @NotBlank String role,
            Long branchId,
            @NotBlank String username,
            String passwordHash,
            @NotBlank String displayName,
            Boolean active,
            Boolean commissionEnabled,
            String commissionType,
            BigDecimal commissionValue
    ) {
    }
}
