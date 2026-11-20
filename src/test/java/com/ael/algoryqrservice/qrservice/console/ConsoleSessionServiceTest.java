package com.ael.algoryqrservice.qrservice.console;

import com.ael.algoryqrservice.qrservice.branch.service.BranchService;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleCatalogService;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleRateSetting;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.ConsoleRateSettingRepository;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.ApplicationEventPublisher;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionFinishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsoleSessionServiceTest {

    private static final long MERCHANT = 13L;
    private static final long BRANCH = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 21, 30, 0);

    @Mock
    private ConsoleSessionRepository sessionRepository;
    @Mock
    private GameConsoleRepository consoleRepository;
    @Mock
    private ConsoleRateSettingRepository rateSettingRepository;
    @Mock
    private BranchService branchService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ConsoleCatalogService catalogService;
    private ConsoleSessionService sessionService;

    @BeforeEach
    void setUp() {
        AppTime.setClock(Clock.fixed(NOW.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
        catalogService = new ConsoleCatalogService(
                consoleRepository, rateSettingRepository, sessionRepository, branchService, securityUtils);
        sessionService = new ConsoleSessionService(
                sessionRepository, consoleRepository, catalogService, branchService, securityUtils, eventPublisher);
        when(securityUtils.getCurrentMerchantId()).thenReturn(MERCHANT);
        when(sessionRepository.save(any(ConsoleSession.class))).thenAnswer(inv -> {
            ConsoleSession s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(500L);
            }
            return s;
        });
    }

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    @Test
    void start_usesConsoleRateOverBranchDefault() {
        GameConsole console = console(new BigDecimal("150.00"));
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console));
        when(rateSettingRepository.findByBranchIdAndMerchantId(BRANCH, MERCHANT))
                .thenReturn(Optional.of(rate(new BigDecimal("100.00"))));

        ConsoleDtos.SessionResponse response = sessionService.start(
                7L, new ConsoleDtos.StartSessionRequest("  Ahmet ", " "));

        assertThat(response.hourlyRate()).isEqualByComparingTo("150.00");
        assertThat(response.startedAt()).isEqualTo(NOW);
        assertThat(response.active()).isTrue();
        assertThat(response.customerName()).isEqualTo("Ahmet");
        assertThat(response.note()).isNull();
        assertThat(response.consoleName()).isEqualTo("PS5 - 1");
    }

    @Test
    void start_withoutConsoleRate_usesBranchDefault() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(null)));
        when(rateSettingRepository.findByBranchIdAndMerchantId(BRANCH, MERCHANT))
                .thenReturn(Optional.of(rate(new BigDecimal("100.00"))));

        assertThat(sessionService.start(7L, null).hourlyRate()).isEqualByComparingTo("100.00");
    }

    @Test
    void start_withNoRateAnywhere_isBadRequest() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(null)));
        when(rateSettingRepository.findByBranchIdAndMerchantId(BRANCH, MERCHANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.start(7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void start_whenConsoleBusy_isConflict() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.of(console(BigDecimal.TEN)));
        when(sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(7L)).thenReturn(true);

        assertThatThrownBy(() -> sessionService.start(7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void start_onAnotherMerchantsConsole_isNotFound() {
        when(consoleRepository.lockOwned(7L, MERCHANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.start(7L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Konsol bulunamadı");
    }

    @Test
    void finish_storesDurationAndAmount() {
        ConsoleSession open = openSession(NOW.minusMinutes(95).minusSeconds(20), new BigDecimal("120.00"));
        when(sessionRepository.lockOwned(500L, MERCHANT)).thenReturn(Optional.of(open));
        when(consoleRepository.findByIdIn(List.of(7L))).thenReturn(List.of(console(null)));

        ConsoleDtos.SessionResponse response = sessionService.finish(500L);

        ArgumentCaptor<ConsoleSession> saved = ArgumentCaptor.forClass(ConsoleSession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getEndedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getDurationMinutes()).isEqualTo(96);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("192.00");
        assertThat(response.active()).isFalse();
        assertThat(response.durationMinutes()).isEqualTo(96);
        assertThat(response.amount()).isEqualByComparingTo("192.00");
        assertThat(response.consoleName()).isEqualTo("PS5 - 1");
        verify(eventPublisher).publishEvent(new ConsoleSessionFinishedEvent(500L));
    }

    @Test
    void finish_whenAlreadyFinished_isConflict() {
        ConsoleSession done = openSession(NOW.minusHours(1), BigDecimal.TEN);
        done.setEndedAt(NOW.minusMinutes(5));
        when(sessionRepository.lockOwned(500L, MERCHANT)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> sessionService.finish(500L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("zaten bitmiş");
        verify(sessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void finish_onAnotherMerchantsSession_isNotFound() {
        when(sessionRepository.lockOwned(500L, MERCHANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.finish(500L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Oturum bulunamadı");
    }

    @Test
    void listActive_showsRunningTotals() {
        when(branchService.requireOwned(BRANCH)).thenReturn(branch());
        when(sessionRepository.findByBranchIdAndMerchantIdAndEndedAtIsNullAndDeletedFalseOrderByStartedAtAscIdAsc(
                BRANCH, MERCHANT)).thenReturn(List.of(openSession(NOW.minusMinutes(30), new BigDecimal("80.00"))));
        when(consoleRepository.findByIdIn(List.of(7L))).thenReturn(List.of(console(null)));

        List<ConsoleDtos.SessionResponse> active = sessionService.listActive(BRANCH);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).durationMinutes()).isEqualTo(30);
        assertThat(active.get(0).amount()).isEqualByComparingTo("40.00");
        assertThat(active.get(0).endedAt()).isNull();
    }

    @Test
    void listActive_onAnotherMerchantsBranch_isNotFound() {
        when(branchService.requireOwned(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı"));

        assertThatThrownBy(() -> sessionService.listActive(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Şube bulunamadı");
    }

    @Test
    void deleteConsole_withOpenSession_isConflict() {
        GameConsole console = console(BigDecimal.TEN);
        when(consoleRepository.findByIdAndMerchantIdAndDeletedFalse(7L, MERCHANT)).thenReturn(Optional.of(console));
        when(sessionRepository.existsByConsoleIdAndEndedAtIsNullAndDeletedFalse(7L)).thenReturn(true);

        assertThatThrownBy(() -> catalogService.delete(7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("açık oturum");
        verify(consoleRepository, never()).save(any());
    }

    private static Branch branch() {
        Branch branch = new Branch();
        branch.setId(BRANCH);
        branch.setMerchantId(MERCHANT);
        return branch;
    }

    private static GameConsole console(BigDecimal hourlyRate) {
        return GameConsole.builder()
                .id(7L)
                .merchantId(MERCHANT)
                .branchId(BRANCH)
                .name("PS5 - 1")
                .hourlyRate(hourlyRate)
                .build();
    }

    private static ConsoleRateSetting rate(BigDecimal value) {
        return ConsoleRateSetting.builder()
                .id(1L)
                .merchantId(MERCHANT)
                .branchId(BRANCH)
                .defaultHourlyRate(value)
                .build();
    }

    private static ConsoleSession openSession(LocalDateTime startedAt, BigDecimal rate) {
        return ConsoleSession.builder()
                .id(500L)
                .merchantId(MERCHANT)
                .branchId(BRANCH)
                .consoleId(7L)
                .hourlyRate(rate)
                .startedAt(startedAt)
                .build();
    }
}
