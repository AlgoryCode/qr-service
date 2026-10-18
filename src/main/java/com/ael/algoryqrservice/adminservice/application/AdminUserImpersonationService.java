package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminUserDtos;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser;
import com.ael.algoryqrservice.authservice.application.auth.AuthSubject;
import com.ael.algoryqrservice.authservice.application.auth.SubjectLoader;
import com.ael.algoryqrservice.authservice.application.auth.TokenUseCase;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.authservice.util.ClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserImpersonationService {

    private final UserRepository userRepository;
    private final SubjectLoader subjectLoader;
    private final TokenUseCase tokenUseCase;
    private final DashboardSecurityUtils dashboardSecurityUtils;

    @Transactional
    public AdminUserDtos.ImpersonateResponse impersonate(Long merchantId, ClientInfo clientInfo) {
        User user = userRepository.findById(merchantId)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));

        AuthSubject subject = subjectLoader.load(AuthSubject.TYPE_MERCHANT, merchantId)
                .orElseThrow(() -> new NotFoundException("Üye oturumu oluşturulamadı"));

        Map<String, Object> tokens = tokenUseCase.create(subject, clientInfo);
        DashboardUser admin = dashboardSecurityUtils.getCurrentDashboardUser();

        return AdminUserDtos.ImpersonateResponse.builder()
                .accessToken(String.valueOf(tokens.get("accessToken")))
                .refreshToken(String.valueOf(tokens.get("refreshToken")))
                .merchantId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .impersonatorUserId(admin.getId())
                .build();
    }
}
