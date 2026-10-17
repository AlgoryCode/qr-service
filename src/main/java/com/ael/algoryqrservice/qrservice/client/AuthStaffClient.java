package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.authservice.application.auth.UpsertStaffUseCase;
import com.ael.algoryqrservice.qrservice.model.MerchantStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthStaffClient {

    private final UpsertStaffUseCase upsertStaffUseCase;

    public void upsert(MerchantStaff staff) {
        if (staff == null || staff.getId() == null) {
            return;
        }
        upsertStaffUseCase.upsert(
                staff.getId(),
                new UpsertStaffUseCase.Command(
                        staff.getMerchantId(),
                        staff.resolvedStaffRole().name(),
                        staff.getBranchId(),
                        staff.getUsername(),
                        staff.getPasswordHash(),
                        staff.getDisplayName(),
                        staff.isActive(),
                        staff.isCommissionEnabled(),
                        staff.getCommissionType() == null ? null : staff.getCommissionType().name(),
                        staff.getCommissionValue()
                )
        );
    }
}
