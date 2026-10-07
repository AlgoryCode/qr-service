package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.AuthCredentialClient;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.UserAccessProfile;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.repository.UserSpecifications;
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
    private final AuthCredentialClient authCredentialClient;

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
                .registrationIpAddress(user.getRegistrationIpAddress())
                .registrationDevice(user.getRegistrationDevice())
                .registrationDeviceType(user.getRegistrationDeviceType())
                .activePackage(accessProfile.activePackage())
                .products(accessProfile.products())
                .scopes(accessProfile.scopes())
                .qrCount(qrRepository.countByUserIdAndDeletedFalse(user.getId()))
                .activeMenuCount(menuRepository.countActiveLiveMenusForUser(user.getId()))
                .purchases(purchases)
                .emailVerified(user.getProvider() == AuthProvider.BASIC ? user.isEmailVerified() : null)
                .build();
    }

    @Transactional
    public AdminUserDtos.UserDetailResponse updateUser(Long id, AdminUserDtos.UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
        applyName(user, request);
        applyPhone(user, request);
        boolean emailChanged = applyEmail(user, request);
        userRepository.save(user);
        if (emailChanged) {
            authCredentialClient.reissueEmailCode(user.getId(), user.getEmail());
        }
        return getUserById(id);
    }

    private void applyName(User user, AdminUserDtos.UserUpdateRequest request) {
        user.setFirstName(request.getFirstName().trim());
        String lastName = request.getLastName() == null ? "" : request.getLastName().trim();
        user.setLastName(lastName.isEmpty() ? null : lastName);
    }

    private void applyPhone(User user, AdminUserDtos.UserUpdateRequest request) {
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        if (phone.isEmpty()) {
            user.setPhone(null);
            return;
        }
        if (!phone.equals(user.getPhone()) && userRepository.existsByPhoneAndIdNot(phone, user.getId())) {
            throw new BadRequestException("Bu telefon numarası zaten kayıtlı");
        }
        user.setPhone(phone);
    }

    private boolean applyEmail(User user, AdminUserDtos.UserUpdateRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (email.equalsIgnoreCase(user.getEmail())) {
            return false;
        }
        if (user.getProvider() != AuthProvider.BASIC) {
            throw new BadRequestException("Google hesabının e-posta adresi uygulama içinden değiştirilemez");
        }
        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
        }
        user.setEmail(email);
        user.setEmailVerified(false);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationSentAt(null);
        return true;
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
