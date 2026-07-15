package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final SecurityUtils securityUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional(readOnly = true)
    public AccountDtos.MyProfileResponse getMyProfile() {
        return toProfileResponse(securityUtils.getCurrentUser());
    }

    @Transactional
    public AccountDtos.MyProfileResponse updateMyProfile(AccountDtos.MyProfilePatchRequest request) {
        User user = securityUtils.getCurrentUser();

        if (request.getFirstName() != null) {
            String firstName = request.getFirstName().trim();
            if (firstName.isBlank()) {
                throw new BadRequestException("İsim boş olamaz");
            }
            user.setFirstName(firstName);
        }

        if (request.getLastName() != null) {
            String lastName = request.getLastName().trim();
            if (lastName.isBlank()) {
                throw new BadRequestException("Soyisim boş olamaz");
            }
            user.setLastName(lastName);
        }

        if (request.getEmail() != null) {
            requireBasicProvider(user, "Google hesabının e-posta adresi uygulama içinden değiştirilemez");
            String email = request.getEmail().trim().toLowerCase();
            if (email.isBlank()) {
                throw new BadRequestException("E-posta boş olamaz");
            }
            if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailAndIdNot(email, user.getId())) {
                throw new BadRequestException("Bu e-posta adresi zaten kayıtlı");
            }
            user.setEmail(email);
        }

        if (request.getPhoneNumber() != null) {
            String phone = request.getPhoneNumber().trim();
            if (phone.isBlank()) {
                throw new BadRequestException("Telefon boş olamaz");
            }
            if (!phone.equals(user.getPhone()) && userRepository.existsByPhoneAndIdNot(phone, user.getId())) {
                throw new BadRequestException("Bu telefon numarası zaten kayıtlı");
            }
            user.setPhone(phone);
        }

        if (request.getNotifyEmailImportant() != null) {
            user.setNotifyEmailImportant(request.getNotifyEmailImportant());
        }
        if (request.getNotifyScanAlerts() != null) {
            user.setNotifyScanAlerts(request.getNotifyScanAlerts());
        }
        if (request.getNotifyWeeklyReport() != null) {
            user.setNotifyWeeklyReport(request.getNotifyWeeklyReport());
        }
        if (request.getNotifyMarketingEmails() != null) {
            user.setNotifyMarketingEmails(request.getNotifyMarketingEmails());
        }
        if (request.getNotifyPushBrowser() != null) {
            user.setNotifyPushBrowser(request.getNotifyPushBrowser());
        }

        return toProfileResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(AccountDtos.ChangePasswordRequest request) {
        User user = securityUtils.getCurrentUser();
        requireBasicProvider(user, "Google hesabı için parola değiştirilemez");

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), request.getCurrentPassword())
        );

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("Yeni şifre mevcut şifre ile aynı olamaz");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private AccountDtos.MyProfileResponse toProfileResponse(User user) {
        return AccountDtos.MyProfileResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhone())
                .provider(user.getProvider())
                .twoFactorEnabled(user.isTwoFactorEnabled())
                .memberSince(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .notifyEmailImportant(user.isNotifyEmailImportant())
                .notifyScanAlerts(user.isNotifyScanAlerts())
                .notifyWeeklyReport(user.isNotifyWeeklyReport())
                .notifyMarketingEmails(user.isNotifyMarketingEmails())
                .notifyPushBrowser(user.isNotifyPushBrowser())
                .build();
    }

    private void requireBasicProvider(User user, String message) {
        if (user.getProvider() != AuthProvider.BASIC) {
            throw new BadRequestException(message);
        }
    }
}
