package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.purchase.lifecycle.ActivePackageResolver;
import com.ael.algoryqrservice.purchase.lifecycle.PackageAccessRestorer;
import com.ael.algoryqrservice.purchase.lifecycle.PackagePeriodExtender;
import com.ael.algoryqrservice.purchase.lifecycle.RemoteSubscriptionCanceller;
import com.ael.algoryqrservice.purchase.lifecycle.UserPackageLifecycleUseCases;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.trial.TrialUseCases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserPackageServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    TrialLogRepository trialLogRepository;
    @Mock
    ActivePackageResolver activePackageResolver;
    @Mock
    RemoteSubscriptionCanceller remoteSubscriptionCanceller;
    @Mock
    PackageAccessRestorer packageAccessRestorer;
    @Mock
    PackagePeriodExtender packagePeriodExtender;
    @Mock
    TrialUseCases trialUseCases;
    @Mock
    PurchaseExpiryService purchaseExpiryService;
    @Mock
    PackageActivationService packageActivationService;
    @Mock
    PurchaseLogService purchaseLogService;

    AdminUserPackageService service;

    @BeforeEach
    void setUp() {
        UserPackageLifecycleUseCases useCases = new UserPackageLifecycleUseCases(
                userRepository,
                trialLogRepository,
                activePackageResolver,
                remoteSubscriptionCanceller,
                packageAccessRestorer,
                packagePeriodExtender,
                trialUseCases,
                purchaseExpiryService,
                packageActivationService,
                purchaseLogService
        );
        service = new AdminUserPackageService(useCases);
    }

    @Test
    void deactivate_whenActive_thenExpireAndLog() {
        User user = User.builder().id(7L).build();
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageName("Pro")
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(activePackageResolver.currentActive(7L)).thenReturn(Optional.of(purchase));

        AdminUserDtos.PackageLifecycleResponse result = service.deactivate(7L);

        assertThat(result.getPurchaseId()).isEqualTo(10L);
        assertThat(purchase.getExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
        verify(remoteSubscriptionCanceller).cancelIfNeeded(purchase);
        verify(purchaseExpiryService).expire(purchase);
        verify(packageActivationService).ensureSubscriptionState(7L);
        verify(purchaseLogService).log(eq(10L), eq(7L), eq(PurchaseLogAction.PURCHASE_DEACTIVATED), anyString());
    }

    @Test
    void deactivate_whenNoActive_thenReject() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));
        when(activePackageResolver.currentActive(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Aktif paket");
        verify(purchaseExpiryService, never()).expire(any());
    }

    @Test
    void reactivate_whenExpired_thenRestore() {
        User user = User.builder().id(7L).build();
        Purchase expired = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageName("Pro")
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.EXPIRED)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(activePackageResolver.latestExpiredSubscriptionLike(7L)).thenReturn(Optional.of(expired));
        when(packageAccessRestorer.restoreActive(expired, 30)).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setStatus(PurchaseStatus.ACTIVE);
            purchase.setExpiresAt(LocalDateTime.now().plusDays(30));
            return purchase;
        });

        AdminUserDtos.PackageLifecycleResponse result = service.reactivate(7L, 30);

        assertThat(result.getDaysAdded()).isEqualTo(30);
        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        verify(packageActivationService).activatePurchasedPackage(expired);
        verify(packageActivationService).ensureSubscriptionState(7L);
        verify(purchaseLogService).log(eq(10L), eq(7L), eq(PurchaseLogAction.PURCHASE_REACTIVATED), anyString());
    }

    @Test
    void reactivate_whenNoExpired_thenReject() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));
        when(activePackageResolver.latestExpiredSubscriptionLike(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivate(7L, 30))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Aktiflestirilecek paket");
        verify(packageAccessRestorer, never()).restoreActive(any(), anyInt());
    }

    @Test
    void updatePackage_whenInactive_thenDeactivate() {
        User user = User.builder().id(7L).build();
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageName("Pro")
                .purchaseType(PurchaseType.PAID)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(activePackageResolver.currentActive(7L)).thenReturn(Optional.of(purchase));

        AdminUserDtos.PackageLifecycleResponse result = service.updatePackage(
                7L,
                AdminUserDtos.PackageUpdateRequest.builder().status("INACTIVE").build()
        );

        assertThat(result.getPurchaseId()).isEqualTo(10L);
        verify(purchaseExpiryService).expire(purchase);
    }

    @Test
    void updatePackage_whenDaysOnlyAndPaid_thenExtend() {
        User user = User.builder().id(7L).build();
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageName("Pro")
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(5))
                .build();
        Purchase extended = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageName("Pro")
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(purchase.getExpiresAt().plusDays(20))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(activePackageResolver.currentActive(7L)).thenReturn(Optional.of(purchase));
        when(packagePeriodExtender.extend(purchase, 20)).thenReturn(extended);

        AdminUserDtos.PackageLifecycleResponse result = service.updatePackage(
                7L,
                AdminUserDtos.PackageUpdateRequest.builder().days(20).build()
        );

        assertThat(result.getDaysAdded()).isEqualTo(20);
        assertThat(result.getPurchaseId()).isEqualTo(10L);
        verify(packagePeriodExtender).extend(purchase, 20);
        verify(packageActivationService).ensureSubscriptionState(7L);
    }

    @Test
    void updatePackage_whenDaysOnlyAndTrial_thenExtendTrial() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));
        when(activePackageResolver.currentActive(7L)).thenReturn(Optional.empty());
        when(trialLogRepository.existsByUserId(7L)).thenReturn(true);
        when(trialUseCases.extend(7L, 15)).thenReturn(AdminUserDtos.ExtendTrialResponse.builder()
                .purchaseId(11L)
                .packageName("Ultimate Deneme")
                .expiresAt(LocalDateTime.now().plusDays(15))
                .daysAdded(15)
                .build());

        AdminUserDtos.PackageLifecycleResponse result = service.updatePackage(
                7L,
                AdminUserDtos.PackageUpdateRequest.builder().days(15).build()
        );

        assertThat(result.getPurchaseId()).isEqualTo(11L);
        assertThat(result.getPackageName()).isEqualTo("Ultimate Deneme");
        assertThat(result.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        verify(trialUseCases).extend(7L, 15);
    }
}
