package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.trial.TrialSnapshotQuery;
import com.ael.algoryqrservice.trial.TrialUseCases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class AdminTrialServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PurchaseRepository purchaseRepository;
    @Mock
    TrialLogRepository trialLogRepository;
    @Mock
    PlanPackageRepository packageRepository;
    @Mock
    FulfillmentGrantService fulfillmentGrantService;

    AdminTrialService service;

    @BeforeEach
    void setUp() {
        TrialSnapshotQuery snapshotQuery = new TrialSnapshotQuery(trialLogRepository, purchaseRepository);
        TrialUseCases useCases = new TrialUseCases(
                userRepository,
                trialLogRepository,
                packageRepository,
                snapshotQuery,
                fulfillmentGrantService
        );
        service = new AdminTrialService(useCases);
    }

    @Test
    void extendTrial_whenActiveLog_thenAddDaysFromCurrentExpiry() {
        User user = User.builder().id(7L).build();
        LocalDateTime currentExpiry = LocalDateTime.now().plusDays(5);
        TrialLog log = activeLog(currentExpiry);
        PlanPackage plan = ultimatePackage();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(trialLogRepository.findByUserId(7L)).thenReturn(Optional.of(log));
        when(trialLogRepository.save(log)).thenReturn(log);
        when(packageRepository.findByIdWithItems(3L)).thenReturn(Optional.of(plan));

        AdminUserDtos.ExtendTrialResponse result = service.extendTrial(7L, 15);

        assertThat(result.getDaysAdded()).isEqualTo(15);
        assertThat(log.getEndsAt()).isEqualTo(currentExpiry.plusDays(15));
        verify(fulfillmentGrantService).grantOnboardingFulfillment(log, plan);
        verify(fulfillmentGrantService).extendOnboardingPeriod(log);
    }

    @Test
    void extendTrial_whenNoLog_thenCreateOnboardingLog() {
        User user = User.builder().id(7L).build();
        PlanPackage ultimate = ultimatePackage();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(trialLogRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(packageRepository.findByCodeWithItems(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)).thenReturn(Optional.of(ultimate));
        when(packageRepository.findByIdWithItems(3L)).thenReturn(Optional.of(ultimate));
        when(trialLogRepository.save(any())).thenAnswer(invocation -> {
            TrialLog log = invocation.getArgument(0);
            log.setId(11L);
            return log;
        });

        AdminUserDtos.ExtendTrialResponse result = service.extendTrial(7L, 30);

        assertThat(result.getPackageName()).isEqualTo("Ultimate Deneme");
        assertThat(result.getDaysAdded()).isEqualTo(30);
        verify(fulfillmentGrantService).grantOnboardingFulfillment(any(), any());
    }

    @Test
    void extendTrial_whenActivePaidExists_thenReject() {
        User user = User.builder().id(7L).build();
        Purchase paid = Purchase.builder()
                .id(1L)
                .userId(7L)
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(paid));
        when(trialLogRepository.findByUserId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extendTrial(7L, 30))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ucretli paket");
        verify(trialLogRepository, never()).save(any());
    }

    @Test
    void endTrial_whenActive_thenMarkEnded() {
        User user = User.builder().id(7L).build();
        TrialLog log = activeLog(LocalDateTime.now().plusDays(5));

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(trialLogRepository.findByUserId(7L)).thenReturn(Optional.of(log));
        when(trialLogRepository.save(log)).thenReturn(log);

        AdminUserDtos.EndTrialResponse result = service.endTrial(7L);

        assertThat(result.getPurchaseId()).isEqualTo(10L);
        assertThat(log.getStatus()).isEqualTo(TrialLogStatus.ENDED);
        verify(fulfillmentGrantService).expireFulfillmentForTrialLog(10L);
    }

    @Test
    void endTrial_whenNoActiveTrial_thenReject() {
        User user = User.builder().id(7L).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(trialLogRepository.findByUserId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.endTrial(7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Aktif deneme");
        verify(fulfillmentGrantService, never()).expireFulfillmentForTrialLog(any());
    }

    @Test
    void updateTrial_whenEnded_thenEndTrial() {
        User user = User.builder().id(7L).build();
        TrialLog log = activeLog(LocalDateTime.now().plusDays(5));

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(trialLogRepository.findByUserId(7L)).thenReturn(Optional.of(log));
        when(trialLogRepository.save(log)).thenReturn(log);

        Object result = service.updateTrial(7L, AdminUserDtos.TrialUpdateRequest.builder().status("ENDED").build());

        assertThat(result).isInstanceOf(AdminUserDtos.EndTrialResponse.class);
        assertThat(log.getStatus()).isEqualTo(TrialLogStatus.ENDED);
    }

    private static TrialLog activeLog(LocalDateTime endsAt) {
        return TrialLog.builder()
                .id(10L)
                .userId(7L)
                .packageId(3L)
                .packageCode(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .status(TrialLogStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(10))
                .endsAt(endsAt)
                .durationDays(15)
                .build();
    }

    private PlanPackage ultimatePackage() {
        Product product = Product.builder().id(7L).code("QR_CREATE").name("QR").build();
        PlanPackageItem item = PlanPackageItem.builder()
                .id(1L)
                .product(product)
                .quantity(1)
                .unlimited(true)
                .build();
        return PlanPackage.builder()
                .id(3L)
                .code(CatalogPackages.ULTIMATE_TRIAL_PACKAGE)
                .name("Ultimate Deneme")
                .price(BigDecimal.ZERO)
                .currency("TRY")
                .validityDays(15)
                .active(true)
                .purchasable(false)
                .systemManaged(false)
                .items(new ArrayList<>(List.of(item)))
                .build();
    }
}
