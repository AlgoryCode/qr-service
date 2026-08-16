package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.DashboardUser;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.repository.UserSpecifications;
import com.ael.algoryqrservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final UserAccessProfileService userAccessProfileService;
    private final PurchaseService purchaseService;
    private final QrRepository qrRepository;
    private final MenuRepository menuRepository;
    private final SessionService sessionService;

    @Transactional(readOnly = true)
    public AdminUserDtos.UserPageResponse listUsers(String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        String trimmed = query == null ? "" : query.trim();
        Page<User> result = trimmed.isEmpty()
                ? userRepository.findAll(pageable)
                : userRepository.findAll(UserSpecifications.forAdminSearch(trimmed), pageable);

        return toPageResponse(result);
    }

    @Transactional
    public AdminUserDtos.UserDetailResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));

        UserAccessProfile accessProfile = userAccessProfileService.resolve(user.getId());
        List<PurchaseResponse> purchases = purchaseService.getUserPurchases(user.getId());

        return AdminUserDtos.UserDetailResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .provider(user.getProvider())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .trialUsed(user.isTrialUsed() || user.getTrialEndDate() != null)
                .trialEndDate(user.getTrialEndDate())
                .registrationIpAddress(user.getRegistrationIpAddress())
                .registrationDevice(user.getRegistrationDevice())
                .registrationDeviceType(user.getRegistrationDeviceType())
                .activePackage(accessProfile.activePackage())
                .products(accessProfile.products())
                .scopes(accessProfile.scopes())
                .qrCount(qrRepository.countByUserIdAndDeletedFalse(user.getId()))
                .activeMenuCount(menuRepository.countActiveLiveMenusForUser(user.getId()))
                .purchases(purchases)
                .build();
    }

    @Transactional
    public AdminUserDtos.ImpersonateResponse impersonateUser(
            Long userId,
            DashboardUser adminUser,
            ClientInfo clientInfo
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));

        if (user.getRole() == UserRole.WAITER) {
            throw new NotFoundException("Garson hesapları için üye girişi desteklenmiyor");
        }

        SessionService.SessionTokens tokens = sessionService.createImpersonationSession(
                user,
                adminUser.getId(),
                clientInfo
        );

        return AdminUserDtos.ImpersonateResponse.builder()
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .impersonatorUserId(adminUser.getId())
                .build();
    }

    private AdminUserDtos.UserPageResponse toPageResponse(Page<User> result) {
        return AdminUserDtos.UserPageResponse.builder()
                .content(result.getContent().stream().map(this::toSummary).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }

    private AdminUserDtos.UserSummaryResponse toSummary(User user) {
        return AdminUserDtos.UserSummaryResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .provider(user.getProvider())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
