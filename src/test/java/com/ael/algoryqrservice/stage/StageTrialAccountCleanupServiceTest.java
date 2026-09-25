package com.ael.algoryqrservice.stage;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StageTrialAccountCleanupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 9, 0);

    @Mock
    private StageTrialAssignmentRepository assignmentRepository;
    @Mock
    private TrialLogRepository trialLogRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthMerchantSoftDeleteClient authMerchantSoftDeleteClient;

    private StageTrialFixtureProperties properties;
    private StageTrialAccountCleanupTx cleanupTx;
    private StageTrialAccountCleanupService service;

    @BeforeEach
    void setUp() {
        AppTime.setClock(Clock.fixed(NOW.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
        properties = new StageTrialFixtureProperties();
        properties.setEnabled(true);
        properties.setTemplateUserId(1L);
        properties.setTemplateBranchId(10L);
        properties.setTemplateMenuId(20L);
        cleanupTx = new StageTrialAccountCleanupTx(trialLogRepository, purchaseRepository, userRepository);
        service = new StageTrialAccountCleanupService(
                properties,
                assignmentRepository,
                authMerchantSoftDeleteClient,
                cleanupTx
        );
    }

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    @Test
    void purgeDue_whenDisabled_thenDoesNothing() {
        properties.setEnabled(false);

        service.purgeDue();

        verify(assignmentRepository, never()).findUserIdsExcept(1L);
    }

    @Test
    void isDue_whenPaidPackageUsable_thenFalse() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));
        when(trialLogRepository.findByUserId(7L)).thenReturn(Optional.of(endedTrial()));
        when(purchaseRepository.findByUserIdAndStatusAndPurchaseType(7L, PurchaseStatus.ACTIVE, PurchaseType.PAID))
                .thenReturn(List.of(usablePaid()));

        assertThat(cleanupTx.isDue(7L)).isFalse();
    }

    @Test
    void purgeDue_whenTrialEnded_thenSoftDeletesAccount() {
        when(assignmentRepository.findUserIdsExcept(1L)).thenReturn(List.of(7L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).phone("555").providerSubject("sub").build()));
        when(trialLogRepository.findByUserId(7L)).thenReturn(Optional.of(endedTrial()));
        when(purchaseRepository.findByUserIdAndStatusAndPurchaseType(7L, PurchaseStatus.ACTIVE, PurchaseType.PAID))
                .thenReturn(List.of());

        service.purgeDue();

        verify(authMerchantSoftDeleteClient).softDelete(7L);
        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getPhone()).isNull();
        assertThat(captor.getValue().getProviderSubject()).isNull();
    }

    private static TrialLog endedTrial() {
        return TrialLog.builder()
                .userId(7L)
                .status(TrialLogStatus.ENDED)
                .endsAt(NOW.minusDays(1))
                .build();
    }

    private static Purchase usablePaid() {
        return Purchase.builder()
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .paymentStyle(PaymentStyle.ONE_TIME)
                .startsAt(NOW.minusDays(1))
                .expiresAt(NOW.plusDays(10))
                .build();
    }
}
