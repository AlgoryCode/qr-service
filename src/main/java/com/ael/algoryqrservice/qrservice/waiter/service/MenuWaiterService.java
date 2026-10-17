package com.ael.algoryqrservice.qrservice.waiter.service;

import com.ael.algoryqrservice.qrservice.client.AuthStaffClient;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.MerchantStaff;
import com.ael.algoryqrservice.qrservice.menu.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.qrservice.model.enums.StaffRole;
import com.ael.algoryqrservice.qrservice.model.enums.WaiterCommissionType;
import com.ael.algoryqrservice.qrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import com.ael.algoryqrservice.qrservice.branch.service.BranchService;

@Service
@RequiredArgsConstructor
public class MenuWaiterService {

    private final MerchantStaffRepository merchantStaffRepository;
    private final BranchService branchService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final AuthStaffClient authStaffClient;

    @Transactional(readOnly = true)
    public MenuWaiterDtos.StaffListResponse listMerchantStaff() {
        Long merchantId = securityUtils.getCurrentMerchantId();
        List<MenuWaiterDtos.WaiterResponse> staff = merchantStaffRepository
                .findByMerchantIdOrderByDisplayNameAsc(merchantId)
                .stream()
                .map(this::toWaiterResponse)
                .toList();
        return MenuWaiterDtos.StaffListResponse.builder()
                .staff(staff)
                .build();
    }

    @Transactional(readOnly = true)
    public MenuWaiterDtos.UsersPageResponse listWaiters(Long branchId) {
        Branch branch = requireOwnedBranch(branchId);
        List<MenuWaiterDtos.WaiterResponse> waiters = merchantStaffRepository
                .findByBranchIdOrderByDisplayNameAsc(branch.getId())
                .stream()
                .map(this::toWaiterResponse)
                .toList();
        return MenuWaiterDtos.UsersPageResponse.builder()
                .waiters(waiters)
                .build();
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse createMerchantStaff(MenuWaiterDtos.CreateWaiterRequest request) {
        return persistStaff(securityUtils.getCurrentMerchantId(), null, request);
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse createWaiter(Long branchId, MenuWaiterDtos.CreateWaiterRequest request) {
        Branch branch = requireOwnedBranch(branchId);
        return persistStaff(branch.getMerchantId(), branch, request);
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse assignBranch(Long staffId, Long branchId) {
        MerchantStaff staff = requireMerchantStaff(staffId);
        if (branchId == null) {
            staff.setBranchId(null);
        } else {
            Branch branch = requireOwnedBranch(branchId);
            assertRoleAllowedOnBranch(staff.resolvedStaffRole(), branch);
            staff.setBranchId(branch.getId());
        }
        staff.setUpdatedAt(LocalDateTime.now());
        return toWaiterResponse(saveAndSync(staff));
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse updateWaiter(
            Long branchId,
            Long staffId,
            MenuWaiterDtos.UpdateWaiterRequest request
    ) {
        requireOwnedBranch(branchId);
        return applyUpdate(requireWaiter(branchId, staffId), request);
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse updateMerchantStaff(
            Long staffId,
            MenuWaiterDtos.UpdateWaiterRequest request
    ) {
        return applyUpdate(requireMerchantStaff(staffId), request);
    }

    private MenuWaiterDtos.WaiterResponse applyUpdate(
            MerchantStaff waiter,
            MenuWaiterDtos.UpdateWaiterRequest request
    ) {
        if (request != null) {
            if (request.getDisplayName() != null) {
                waiter.setDisplayName(requireDisplayName(request.getDisplayName()));
            }
            if (request.getActive() != null) {
                waiter.setActive(request.getActive());
            }
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                waiter.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            }
            if (request.getCommissionEnabled() != null) {
                waiter.setCommissionEnabled(request.getCommissionEnabled());
                if (!request.getCommissionEnabled()) {
                    waiter.setCommissionType(null);
                    waiter.setCommissionValue(null);
                }
            }
            if (request.getCommissionType() != null) {
                waiter.setCommissionType(request.getCommissionType());
            }
            if (request.getCommissionValue() != null) {
                validateCommissionValue(request.getCommissionType(), request.getCommissionValue());
                waiter.setCommissionValue(request.getCommissionValue());
            }
        }

        waiter.setUpdatedAt(LocalDateTime.now());
        return toWaiterResponse(saveAndSync(waiter));
    }

    @Transactional
    public void deleteWaiter(Long branchId, Long staffId) {
        requireOwnedBranch(branchId);
        deactivate(requireWaiter(branchId, staffId));
    }

    @Transactional
    public void deleteMerchantStaff(Long staffId) {
        deactivate(requireMerchantStaff(staffId));
    }

    private void deactivate(MerchantStaff waiter) {
        waiter.setActive(false);
        waiter.setUpdatedAt(LocalDateTime.now());
        saveAndSync(waiter);
    }

    private MenuWaiterDtos.WaiterResponse persistStaff(
            Long merchantId,
            Branch branch,
            MenuWaiterDtos.CreateWaiterRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("İstek gövdesi zorunludur");
        }

        String username = normalizeUsername(request.getUsername());
        if (merchantStaffRepository.existsByMerchantIdAndUsernameIgnoreCase(merchantId, username)) {
            throw new BadRequestException("Bu kullanıcı adı bu işletmede zaten kullanılıyor");
        }

        String displayName = requireDisplayName(request.getDisplayName());
        StaffRole staffRole = request.getStaffRole() == null ? StaffRole.WAITER : request.getStaffRole();
        if (branch != null) {
            assertRoleAllowedOnBranch(staffRole, branch);
        }
        LocalDateTime now = LocalDateTime.now();

        MerchantStaff waiter = MerchantStaff.builder()
                .merchantId(merchantId)
                .branchId(branch == null ? null : branch.getId())
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(displayName)
                .staffRole(staffRole)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toWaiterResponse(saveAndSync(waiter));
    }

    private MerchantStaff saveAndSync(MerchantStaff staff) {
        MerchantStaff saved = merchantStaffRepository.save(staff);
        authStaffClient.upsert(saved);
        return saved;
    }

    private void assertRoleAllowedOnBranch(StaffRole staffRole, Branch branch) {
        if (staffRole == StaffRole.KITCHEN && !branch.isKitchenEnabled()) {
            throw new BadRequestException("Önce şube ayarlarından mutfağı ekleyin");
        }
        if (staffRole == StaffRole.COURIER && !branch.isCourierEnabled()) {
            throw new BadRequestException("Önce şube bilgilerinden kuryeyi açın");
        }
    }

    private Branch requireOwnedBranch(Long branchId) {
        return branchService.requireOwnedForUser(branchId, securityUtils.getCurrentMerchantId());
    }

    private MerchantStaff requireWaiter(Long branchId, Long staffId) {
        return merchantStaffRepository.findByIdAndBranchId(staffId, branchId)
                .orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
    }

    private MerchantStaff requireMerchantStaff(Long staffId) {
        return merchantStaffRepository.findByIdAndMerchantId(staffId, securityUtils.getCurrentMerchantId())
                .orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
    }

    private MenuWaiterDtos.WaiterResponse toWaiterResponse(MerchantStaff waiter) {
        return MenuWaiterDtos.WaiterResponse.builder()
                .id(waiter.getId())
                .branchId(waiter.getBranchId())
                .username(waiter.getUsername())
                .displayName(waiter.getDisplayName())
                .staffRole(waiter.resolvedStaffRole())
                .active(waiter.isActive())
                .commissionEnabled(waiter.isCommissionEnabled())
                .commissionType(waiter.getCommissionType())
                .commissionValue(waiter.getCommissionValue())
                .createdAt(waiter.getCreatedAt())
                .build();
    }

    private void validateCommissionValue(WaiterCommissionType type, java.math.BigDecimal value) {
        if (value == null || value.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Komisyon değeri geçersiz");
        }
        if (type == WaiterCommissionType.PERCENT && value.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Yüzde komisyon 100'den büyük olamaz");
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("Kullanıcı adı zorunludur");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new BadRequestException("Görünen ad zorunludur");
        }
        return displayName.trim();
    }
}
