package com.ael.algoryqrservice.staffservice.application;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.enums.StaffRole;
import com.ael.algoryqrservice.qrservice.model.enums.WaiterCommissionType;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpsertStaffUseCase {

    static final Set<String> STAFF_ROLES = Set.of("WAITER", "KITCHEN", "COURIER");

    private final MerchantStaffRepository staffRepository;

    @Transactional
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

        staffRepository.findByUsernameIgnoreCase(username)
                .filter(existing -> !staffId.equals(existing.getId()))
                .ifPresent(existing -> {
                    throw new BadRequestException("Bu kullanıcı adı başka bir personelde kullanılıyor");
                });

        MerchantStaff staff = staffRepository.findById(staffId).orElseGet(MerchantStaff::new);
        boolean created = staff.getId() == null;
        String passwordHash = command.passwordHash() == null ? "" : command.passwordHash().trim();
        if (created) {
            if (passwordHash.isBlank()) {
                throw new BadRequestException("Şifre hash gerekli");
            }
            staff.setId(staffId);
            LocalDateTime now = LocalDateTime.now();
            staff.setCreatedAt(now);
            staff.setUpdatedAt(now);
        }

        staff.setMerchantId(command.merchantId());
        staff.setStaffRole(StaffRole.valueOf(roleCode));
        staff.setBranchId(command.branchId());
        staff.setUsername(username);
        staff.setDisplayName(displayName);
        staff.setActive(command.active() == null || command.active());
        if (!passwordHash.isBlank()) {
            staff.setPasswordHash(passwordHash);
        }
        staff.setCommissionEnabled(Boolean.TRUE.equals(command.commissionEnabled()));
        staff.setCommissionType(parseCommissionType(command.commissionType()));
        staff.setCommissionValue(command.commissionValue());
        if (!created) {
            staff.setUpdatedAt(LocalDateTime.now());
        }
        staffRepository.save(staff);
        log.info("[staff] Upsert staffId={} merchantId={} created={}", staffId, command.merchantId(), created);
    }

    private static WaiterCommissionType parseCommissionType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        return WaiterCommissionType.valueOf(normalized);
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
