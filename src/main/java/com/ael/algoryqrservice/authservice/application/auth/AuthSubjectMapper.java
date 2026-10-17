package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.AdminEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantStaffEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.ScopeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class AuthSubjectMapper {

    private final ScopeJpaRepository scopeRepository;

    public AuthSubject fromStaff(MerchantStaffEntity staff) {
        return new AuthSubject(
                AuthSubject.TYPE_STAFF,
                staff.getId(),
                staff.getRole().getId(),
                staff.getRole().getCode(),
                staff.getPasswordHash(),
                staff.getDisplayName(),
                staff.getUsername(),
                "BASIC",
                staff.getMerchantId(),
                staff.getBranchId(),
                scopeRepository.findCodesByRoleId(staff.getRole().getId())
        );
    }

    public AuthSubject fromMerchant(MerchantEntity merchant) {
        return new AuthSubject(
                AuthSubject.TYPE_MERCHANT,
                merchant.getId(),
                merchant.getRole().getId(),
                merchant.getRole().getCode(),
                merchant.getPassword(),
                displayName(merchant.getFirstName(), merchant.getLastName()),
                merchant.getEmail(),
                merchant.getProvider(),
                merchant.getId(),
                null,
                scopeRepository.findCodesByRoleId(merchant.getRole().getId())
        );
    }

    public AuthSubject fromAdmin(AdminEntity admin) {
        return new AuthSubject(
                AuthSubject.TYPE_ADMIN,
                admin.getId(),
                admin.getRole().getId(),
                admin.getRole().getCode(),
                admin.getPassword(),
                displayName(admin.getFirstName(), admin.getLastName()),
                admin.getEmail(),
                "BASIC",
                null,
                null,
                scopeRepository.findCodesByRoleId(admin.getRole().getId())
        );
    }

    static String displayName(String first, String last) {
        StringBuilder name = new StringBuilder();
        if (first != null && !first.isBlank()) {
            name.append(first.trim());
        }
        if (last != null && !last.isBlank()) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(last.trim());
        }
        return name.toString();
    }
}
