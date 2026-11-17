package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.DashboardAuthDtos.UpdateDashboardUserRequest;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.DashboardUser;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.enums.DashboardRole;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.DashboardUserRepository;
import com.ael.algoryqrservice.adminservice.security.Totp;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.util.QrCodeGeneratorUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardAccountServiceTest {

    private DashboardUserRepository repository;
    private DashboardSessionService sessions;
    private PasswordEncoder encoder;
    private DashboardAccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(DashboardUserRepository.class);
        sessions = mock(DashboardSessionService.class);
        encoder = mock(PasswordEncoder.class);
        service = new DashboardAccountService(repository, encoder, sessions, mock(QrCodeGeneratorUtil.class));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private DashboardUser user(long id, DashboardRole role, boolean active) {
        DashboardUser u = DashboardUser.builder().id(id).email(id + "@x.com").role(role).active(active).password("h").build();
        when(repository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void cannotDemoteTheLastActiveSuperAdmin() {
        DashboardUser only = user(1, DashboardRole.ADMIN, true);
        DashboardUser inactive = user(2, DashboardRole.ADMIN, false);
        when(repository.findAll()).thenReturn(List.of(only, inactive));
        assertThatThrownBy(() -> service.update(1L, new UpdateDashboardUserRequest(null, null, null, DashboardRole.VIEWER, null), 9L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void roleChangeSignsTheAccountOut() {
        DashboardUser a = user(1, DashboardRole.ADMIN, true);
        DashboardUser b = user(2, DashboardRole.ADMIN, true);
        when(repository.findAll()).thenReturn(List.of(a, b));
        service.update(2L, new UpdateDashboardUserRequest(null, null, null, DashboardRole.FINANCE, null), 1L);
        assertThat(b.getRole()).isEqualTo(DashboardRole.FINANCE);
        verify(sessions).revokeAll(2L);
    }

    @Test
    void cannotDeactivateYourself() {
        user(4, DashboardRole.SUPPORT, true);
        assertThatThrownBy(() -> service.update(4L, new UpdateDashboardUserRequest(null, null, null, null, false), 4L))
                .isInstanceOf(BadRequestException.class);
        verify(sessions, never()).revokeAll(4L);
    }

    @Test
    void mfaTurnsOnOnlyWithAValidCode() {
        DashboardUser u = user(5, DashboardRole.SUPPORT, true);
        service.startMfaSetup(5L);
        assertThat(u.getTotpSecret()).isNotBlank();
        assertThatThrownBy(() -> service.enableMfa(5L, "000000")).isInstanceOf(BadRequestException.class);
        long now = System.currentTimeMillis() / 1000;
        service.enableMfa(5L, codeNow(u.getTotpSecret(), now));
        assertThat(u.isTotpEnabled()).isTrue();
        service.resetMfa(5L);
        assertThat(u.isTotpEnabled()).isFalse();
        assertThat(u.getTotpSecret()).isNull();
    }

    @Test
    void changeOwnPasswordChecksTheCurrentOne() {
        user(6, DashboardRole.VIEWER, true);
        when(encoder.matches("wrong", "h")).thenReturn(false);
        assertThatThrownBy(() -> service.changeOwnPassword(6L, "wrong", "new-password-123"))
                .isInstanceOf(BadRequestException.class);
    }

    private static String codeNow(String secret, long epochSeconds) {
        for (int i = 0; i < 1_000_000; i++) {
            String code = String.format("%06d", i);
            if (Totp.verify(secret, code, epochSeconds)) {
                return code;
            }
        }
        throw new AssertionError("no code");
    }
}
