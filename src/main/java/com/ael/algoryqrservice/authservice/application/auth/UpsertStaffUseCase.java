package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.exception.BadRequestException;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.MerchantStaffEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.RoleEntity;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.MerchantStaffJpaRepository;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.repository.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ael.algoryqrservice.authservice.config.AuthTransactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UpsertStaffUseCase {

    static final Set<String> STAFF_ROLES = Set.of("WAITER", "KITCHEN", "COURIER");

    private final MerchantStaffJpaRepository staffRepository;
    private final RoleJpaRepository roleRepository;

    @AuthTransactional
    public void upsert(Long staffId, Command command) {
        if (staffId == null || staffId <= 0) {
            throw new BadRequestException("Personel id gerekli");
        }
        if (command.merchantId() == null || command.merchantId() <= 0) {
            throw new BadRequestException("İşletme id gerekli");
        }
        String roleCode = command.role() == null ? "" : command.role().trim().toUpperCase(Locale.ROOT);
        if (!STAFF_ROLES.contains(roleCode)) {
            throw new BadRequestException("Personel rolü geçersiz");
        }
        String username = command.username() == null ? "" : command.username().trim().toLowerCase(Locale.ROOT);
        if (username.isBlank() || username.length() > 64) {
            throw new BadRequestException("Kullanıcı adı geçersiz");
        }
        String displayName = command.displayName() == null ? "" : command.displayName().trim();
        if (displayName.isBlank() || displayName.length() > 120) {
            throw new BadRequestException("Görünen ad geçersiz");
        }

        RoleEntity role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BadRequestException("Personel rolü bulunamadı"));
        staffRepository.findByUsernameIgnoreCase(username)
                .filter(existing -> !staffId.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new BadRequestException("Bu kullanıcı adı başka bir personelde kullanılıyor");
                });

        MerchantStaffEntity staff = staffRepository.findById(staffId).orElseGet(MerchantStaffEntity::new);
        boolean created = staff.getId() == null;
        String passwordHash = command.passwordHash() == null ? "" : command.passwordHash().trim();
        if (created) {
            if (passwordHash.isBlank()) {
                throw new BadRequestException("Şifre hash gerekli");
            }
            staff.setId(staffId);
        }

        staff.setMerchantId(command.merchantId());
        staff.setRole(role);
        staff.setBranchId(command.branchId());
        staff.setUsername(username);
        staff.setDisplayName(displayName);
        staff.setActive(command.active() == null || command.active());
        if (!passwordHash.isBlank()) {
            staff.setPasswordHash(passwordHash);
        }
        staff.setCommissionEnabled(Boolean.TRUE.equals(command.commissionEnabled()));
        staff.setCommissionType(blankToNull(command.commissionType()));
        staff.setCommissionValue(command.commissionValue());
        staffRepository.save(staff);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record Command(
            Long merchantId,
            String role,
            Long branchId,
            String username,
            String passwordHash,
            String displayName,
            Boolean active,
            Boolean commissionEnabled,
            String commissionType,
            BigDecimal commissionValue
    ) {
    }
}
