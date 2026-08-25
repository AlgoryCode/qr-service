package com.ael.algoryqrservice.service.entitlement;

import com.ael.algoryqrservice.event.PurchasesExpiredEvent;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.UserTrialService;
import com.ael.algoryqrservice.util.WritableTransactionGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseExpiryServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PurchaseLogService purchaseLogService;
    @Mock
    private UserTrialService userTrialService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private WritableTransactionGuard writableTransactionGuard;

    @InjectMocks
    private PurchaseExpiryService purchaseExpiryService;

    @Captor
    private ArgumentCaptor<PurchasesExpiredEvent> eventCaptor;

    @Test
    void expire_whenActiveTrial_thenMarkExpiredAndAnnounce() {
        Purchase purchase = activeTrial();

        purchaseExpiryService.expire(purchase);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        verify(userTrialService).markTrialCompleted(USER_ID, purchase.getExpiresAt());
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userIds()).containsExactly(USER_ID);
    }

    @Test
    void expire_whenPurchaseIsNotActive_thenDoNotAnnounce() {
        Purchase purchase = activeTrial();
        purchase.setStatus(PurchaseStatus.CANCELLED);

        purchaseExpiryService.expire(purchase);

        verify(purchaseRepository, never()).save(purchase);
        verify(eventPublisher, never()).publishEvent(any(PurchasesExpiredEvent.class));
    }

    @Test
    void expireDueForUser_whenDueExists_thenExpireAndAnnounceOnce() {
        Purchase purchase = activeTrial();
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(true);
        when(purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(
                eq(USER_ID), eq(PurchaseStatus.ACTIVE), any(LocalDateTime.class)
        )).thenReturn(List.of(purchase));

        purchaseExpiryService.expireDueForUser(USER_ID);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        verify(eventPublisher).publishEvent(any(PurchasesExpiredEvent.class));
    }

    @Test
    void expireDueForUser_whenTransactionIsReadOnly_thenSkipEntirely() {
        when(writableTransactionGuard.allowsWrites(any(), eq(USER_ID))).thenReturn(false);

        purchaseExpiryService.expireDueForUser(USER_ID);

        verify(purchaseRepository, never()).findByUserIdAndStatusAndExpiresAtBefore(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(PurchasesExpiredEvent.class));
    }

    private Purchase activeTrial() {
        return Purchase.builder()
                .id(10L)
                .userId(USER_ID)
                .packageName("Pro")
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(8))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }
}
