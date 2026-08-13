package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.dto.LogoutRequest;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.model.dto.RefreshTokenRequest;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.util.ClientInfo;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuWaiterAuthService {

    private final MenuWaiterRepository menuWaiterRepository;
    private final PasswordEncoder passwordEncoder;
    private final MenuWaiterSessionService menuWaiterSessionService;
    private final JwtService jwtService;
    private final SecurityUtils securityUtils;

    @Transactional
    public MenuWaiterDtos.WaiterAuthResponse login(
            MenuWaiterDtos.WaiterLoginRequest request,
            ClientInfo clientInfo
    ) {
        MenuWaiter waiter = authenticate(request);
        MenuWaiterSessionService.SessionTokens tokens = menuWaiterSessionService.createSession(waiter, clientInfo);
        return menuWaiterSessionService.buildAuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                waiter
        );
    }

    public MenuWaiterDtos.WaiterAuthResponse refresh(RefreshTokenRequest request) {
        return menuWaiterSessionService.refresh(request.getRefreshToken());
    }

    @Transactional
    public void logout(LogoutRequest request, String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            var revoked = jwtService.extractSessionIdIfSignatureValid(accessToken)
                    .map(sessionId -> {
                        menuWaiterSessionService.revokeByAccessSessionId(sessionId);
                        return true;
                    })
                    .orElse(false);
            if (revoked) {
                return;
            }
        }

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            menuWaiterSessionService.revokeByRefreshToken(request.getRefreshToken());
            return;
        }

        throw new BadRequestException("Çıkış için geçerli access token veya refresh token gerekli");
    }

    @Transactional(readOnly = true)
    public MenuWaiterDtos.WaiterMeResponse me() {
        Long waiterId = securityUtils.getCurrentWaiterId();
        MenuWaiter waiter = menuWaiterRepository.findById(waiterId)
                .orElseThrow(() -> new BadCredentialsException("Garson bulunamadı"));
        return MenuWaiterDtos.WaiterMeResponse.builder()
                .waiterId(waiter.getId())
                .menuId(waiter.getMenuId())
                .ownerUserId(waiter.getOwnerUserId())
                .username(waiter.getUsername())
                .displayName(waiter.getDisplayName())
                .active(waiter.isActive())
                .build();
    }

    private MenuWaiter authenticate(MenuWaiterDtos.WaiterLoginRequest request) {
        String username = request.getUsername().trim();
        MenuWaiter waiter = menuWaiterRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BadCredentialsException("Geçersiz kimlik bilgileri"));
        if (!waiter.isActive()) {
            throw new BadCredentialsException("Geçersiz kimlik bilgileri");
        }
        if (waiter.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), waiter.getPasswordHash())) {
            throw new BadCredentialsException("Geçersiz kimlik bilgileri");
        }
        return waiter;
    }
}
