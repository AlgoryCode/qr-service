package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.FulfillmentGrantService;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingPackageServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 10, 0);

    @Mock
    UserRepository userRepository;
    @Mock
    PlanPackageRepository packageRepository;
    @Mock
    TrialLogRepository trialLogRepository;
    @Mock
    SessionAccessService sessionAccessService;
    @Mock
    FulfillmentGrantService fulfillmentGrantService;

    private OnboardingPackageService service;

    @BeforeEach
    void setUp() {
        AppTime.setClock(Clock.fixed(NOW.atZone(AppTime.ZONE).toInstant(), AppTime.ZONE));
        service = new OnboardingPackageService(
                userRepository,
                packageRepository,
                trialLogRepository,
                sessionAccessService,
                new SessionAccessPolicy(),
                fulfillmentGrantService
        );
    }

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    @Test
    void start_whenSecondAttempt_thenReject() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(googleUser(NOW.minusDays(2))));
        when(trialLogRepository.existsByUserId(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.start(7L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("daha once");
        verify(trialLogRepository, never()).saveAndFlush(any());
    }

    @Test
    void start_whenAccountOlderThanFifteenDays_thenReject() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(googleUser(NOW.minusDays(16))));

        assertThatThrownBy(() -> service.start(7L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("penceresi");
    }

    @Test
    void start_whenUsablePaidExists_thenReject() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(googleUser(NOW.minusDays(2))));
        when(trialLogRepository.existsByUserId(7L)).thenReturn(false);
        when(sessionAccessService.governingPaid(7L)).thenReturn(Optional.of(usablePaid()));

        assertThatThrownBy(() -> service.start(7L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ucretli paket");
    }

    @Test
    void start_whenEligible_thenInsertLogWithoutPurchase() {
        PlanPackage plan = onboardingPackage();
        when(userRepository.findById(7L)).thenReturn(Optional.of(googleUser(NOW.minusDays(2))));
        when(trialLogRepository.existsByUserId(7L)).thenReturn(false);
        when(sessionAccessService.governingPaid(7L)).thenReturn(Optional.empty());
        when(packageRepository.findByCodeWithItems(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)).thenReturn(Optional.of(plan));
        when(trialLogRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TrialLog log = invocation.getArgument(0);
            log.setId(44L);
            return log;
        });
        when(sessionAccessService.resolve(7L)).thenReturn(AccessSession.of(
                com.ael.algoryqrservice.model.enums.AccessDecision.ALLOW,
                CatalogPackages.ULTIMATE_TRIAL_PACKAGE,
                NOW.plusDays(15),
                null
        ));

        service.start(7L, 3L);

        ArgumentCaptor<TrialLog> captor = ArgumentCaptor.forClass(TrialLog.class);
        verify(trialLogRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPackageCode()).isEqualTo(CatalogPackages.ULTIMATE_TRIAL_PACKAGE);
        assertThat(captor.getValue().getStatus()).isEqualTo(TrialLogStatus.ACTIVE);
        verify(fulfillmentGrantService).grantOnboardingFulfillment(captor.getValue(), plan);
    }

    private static User googleUser(LocalDateTime createdAt) {
        return User.builder()
                .id(7L)
                .provider(AuthProvider.GOOGLE)
                .emailVerified(true)
                .createdAt(createdAt)
                .build();
    }

    private static Purchase usablePaid() {
        return Purchase.builder()
                .id(9L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(NOW.minusDays(1))
                .expiresAt(NOW.plusDays(20))
                .build();
    }

    private static PlanPackage onboardingPackage() {
        Product product = Product.builder().id(1L).code("QR_MENU").name("Menu").build();
        PlanPackageItem item = PlanPackageItem.builder().id(1L).product(product).quantity(1).unlimited(true).build();
        return PlanPackage.builder()
                .id(3L)
                .code(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .name("Ultimate Deneme")
                .validityDays(15)
                .active(true)
                .purchasable(false)
                .systemManaged(false)
                .items(new ArrayList<>(List.of(item)))
                .build();
    }
}
