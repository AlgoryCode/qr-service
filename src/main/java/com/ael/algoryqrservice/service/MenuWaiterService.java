package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.MerchantStaff;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.model.enums.StaffRole;
import com.ael.algoryqrservice.model.enums.WaiterCommissionType;
import com.ael.algoryqrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MenuWaiterService {

    private final MerchantStaffRepository merchantStaffRepository;
    private final UserRepository userRepository;
    private final BranchService branchService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public MenuWaiterDtos.UsersPageResponse listWaiters(Long branchId) {
        Branch branch = requireOwnedBranch(branchId);
        MenuWaiterDtos.OwnerSummary owner = getOwnerSummary(branch);
        List<MenuWaiterDtos.WaiterResponse> waiters = merchantStaffRepository
                .findByBranchIdOrderByDisplayNameAsc(branch.getId())
                .stream()
                .map(this::toWaiterResponse)
                .toList();
        return MenuWaiterDtos.UsersPageResponse.builder()
                .owner(owner)
                .waiters(waiters)
                .build();
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse createWaiter(Long branchId, MenuWaiterDtos.CreateWaiterRequest request) {
        Branch branch = requireOwnedBranch(branchId);
        if (request == null) {
            throw new BadRequestException("İstek gövdesi zorunludur");
        }

        String username = normalizeUsername(request.getUsername());
        if (merchantStaffRepository.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Bu kullanıcı adı zaten kullanılıyor");
        }

        String displayName = requireDisplayName(request.getDisplayName());
        StaffRole staffRole = request.getStaffRole() == null ? StaffRole.WAITER : request.getStaffRole();
        if (staffRole == StaffRole.KITCHEN && !branch.isKitchenEnabled()) {
            throw new BadRequestException("Önce şube ayarlarından mutfağı ekleyin");
        }
        if (staffRole == StaffRole.COURIER && !branch.isCourierEnabled()) {
            throw new BadRequestException("Önce şube bilgilerinden kuryeyi açın");
        }
        LocalDateTime now = LocalDateTime.now();

        MerchantStaff waiter = MerchantStaff.builder()
                .merchantId(branch.getUserId())
                .branchId(branch.getId())
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(displayName)
                .staffRole(staffRole)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toWaiterResponse(merchantStaffRepository.save(waiter));
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse updateWaiter(
            Long branchId,
            Long staffId,
            MenuWaiterDtos.UpdateWaiterRequest request
    ) {
        requireOwnedBranch(branchId);
        MerchantStaff waiter = requireWaiter(branchId, staffId);

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
        return toWaiterResponse(merchantStaffRepository.save(waiter));
    }

    @Transactional
    public void deleteWaiter(Long branchId, Long staffId) {
        requireOwnedBranch(branchId);
        MerchantStaff waiter = requireWaiter(branchId, staffId);
        waiter.setActive(false);
        waiter.setUpdatedAt(LocalDateTime.now());
        merchantStaffRepository.save(waiter);
    }

    private MenuWaiterDtos.OwnerSummary getOwnerSummary(Branch branch) {
        User owner = userRepository.findById(branch.getUserId())
                .orElseThrow(() -> new NotFoundException("İşletme sahibi bulunamadı"));
        return MenuWaiterDtos.OwnerSummary.builder()
                .id(owner.getId())
                .firstName(owner.getFirstName())
                .lastName(owner.getLastName())
                .email(owner.getEmail())
                .build();
    }

    private Branch requireOwnedBranch(Long branchId) {
        return branchService.requireOwnedForUser(branchId, securityUtils.getCurrentUserId());
    }

    private MerchantStaff requireWaiter(Long branchId, Long staffId) {
        return merchantStaffRepository.findByIdAndBranchId(staffId, branchId)
                .orElseThrow(() -> new NotFoundException("Garson bulunamadı"));
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
