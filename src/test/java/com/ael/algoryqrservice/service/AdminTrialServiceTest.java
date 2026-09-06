package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AdminUserDtos;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.service.entitlement.PackageEntitlementWriter;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.trial.TrialSnapshotQuery;
import com.ael.algoryqrservice.trial.TrialUseCases;
import com.ael.algoryqrservice.trial.extend.ActiveTrialExtendHandler;
import com.ael.algoryqrservice.trial.extend.ExpiredTrialExtendHandler;
import com.ael.algoryqrservice.trial.extend.NeverStartedTrialExtendHandler;
import com.ael.algoryqrservice.trial.extend.TrialExtendHandlerRegistry;
import com.ael.algoryqrservice.trial.extend.TrialPurchaseExtender;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    PlanPackageRepository packageRepository;
    @Mock
    PackageEntitlementWriter entitlementWriter;
    @Mock
    PurchaseExpiryService purchaseExpiryService;
    @Mock
    PackageActivationService packageActivationService;
    @Mock
    PurchaseLogService purchaseLogService;

    AdminTrialService service;

    @BeforeEach
    void setUp() {
        TrialSnapshotQuery snapshotQuery = new TrialSnapshotQuery(purchaseRepository, purchaseExpiryService);
        TrialPurchaseExtender extender = new TrialPurchaseExtender(
                purchaseRepository, packageRepository, entitlementWriter, purchaseLogService
        );
        TrialExtendHandlerRegistry registry = new TrialExtendHandlerRegistry(List.of(
                new NeverStartedTrialExtendHandler(
                        purchaseRepository, packageRepository, entitlementWriter, purchaseLogService, extender
                ),
                new ActiveTrialExtendHandler(purchaseRepository, extender),
                new ExpiredTrialExtendHandler(purchaseRepository, extender)
        ));
        TrialUseCases useCases = new TrialUseCases(
                userRepository,
                purchaseRepository,
                snapshotQuery,
                registry,
                packageActivationService,
                purchaseExpiryService,
                purchaseLogService
        );
        service = new AdminTrialService(useCases);
    }

    @Test
    void extendTrial_whenActiveTrial_thenAddDaysFromCurrentExpiry() {
        User user = User.builder().id(7L).build();
        LocalDateTime currentExpiry = LocalDateTime.now().plusDays(5);
        Purchase trial = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageId(3L)
                .packageName("Ultimate")
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(10))
                .expiresAt(currentExpiry)
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(trial));
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.of(trial));
        when(purchaseRepository.save(trial)).thenReturn(trial);
        when(packageRepository.findByIdWithItems(3L)).thenReturn(Optional.empty());

        AdminUserDtos.ExtendTrialResponse result = service.extendTrial(7L, 15);

        assertThat(result.getDaysAdded()).isEqualTo(15);
        assertThat(trial.getExpiresAt()).isEqualTo(currentExpiry.plusDays(15));
        verify(packageActivationService).activatePurchasedPackage(trial);
    }

    @Test
    void extendTrial_whenExpiredTrial_thenReactivateFromNow() {
        User user = User.builder().id(7L).build();
        Purchase trial = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageId(3L)
                .packageName("Ultimate")
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.EXPIRED)
                .startsAt(LocalDateTime.now().minusDays(40))
                .expiresAt(LocalDateTime.now().minusDays(10))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.of(trial));
        when(purchaseRepository.save(trial)).thenReturn(trial);
        when(packageRepository.findByIdWithItems(3L)).thenReturn(Optional.empty());

        service.extendTrial(7L, 30);

        assertThat(trial.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        assertThat(trial.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    void extendTrial_whenNoTrial_thenGrantUltimateTrial() {
        User user = User.builder().id(7L).build();
        PlanPackage ultimate = ultimatePackage();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.empty());
        when(packageRepository.findByCode(CatalogPackages.ULTIMATE_PACKAGE)).thenReturn(Optional.of(ultimate));
        when(packageRepository.findByIdWithItems(3L)).thenReturn(Optional.of(ultimate));
        when(purchaseRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(11L);
            return purchase;
        });

        AdminUserDtos.ExtendTrialResponse result = service.extendTrial(7L, 30);

        assertThat(result.getPackageName()).isEqualTo("Ultimate");
        assertThat(result.getDaysAdded()).isEqualTo(30);
        verify(entitlementWriter).grant(any(), any(), any(), any(Integer.class), any(Boolean.class));
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
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extendTrial(7L, 30))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ucretli paket");
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void extendTrial_whenSystemGrantActive_thenReject() {
        User user = User.builder().id(7L).build();
        Purchase grant = Purchase.builder()
                .id(1L)
                .userId(7L)
                .purchaseType(PurchaseType.SYSTEM_GRANT)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(grant));
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extendTrial(7L, 30))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void endTrial_whenActive_thenExpireAndLog() {
        User user = User.builder().id(7L).build();
        Purchase trial = Purchase.builder()
                .id(10L)
                .userId(7L)
                .packageId(3L)
                .packageName("Ultimate")
                .purchaseType(PurchaseType.TRIAL)
                .status(PurchaseStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().plusDays(5))
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of(trial));
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.of(trial));

        AdminUserDtos.EndTrialResponse result = service.endTrial(7L);

        assertThat(result.getPurchaseId()).isEqualTo(10L);
        assertThat(result.getPackageName()).isEqualTo("Ultimate");
        assertThat(trial.getExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
        verify(purchaseExpiryService).expire(trial);
        verify(packageActivationService).ensureSubscriptionState(7L);
        verify(purchaseLogService).log(
                eq(10L),
                eq(7L),
                eq(PurchaseLogAction.TRIAL_ENDED),
                anyString()
        );
    }

    @Test
    void endTrial_whenNoActiveTrial_thenReject() {
        User user = User.builder().id(7L).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(purchaseRepository.findByUserIdAndStatus(7L, PurchaseStatus.ACTIVE)).thenReturn(List.of());
        when(purchaseRepository.findFirstByUserIdAndPurchaseTypeOrderByPurchasedAtDesc(7L, PurchaseType.TRIAL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.endTrial(7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Aktif deneme");
        verify(purchaseExpiryService, never()).expire(any());
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
                .code(CatalogPackages.ULTIMATE_PACKAGE)
                .name("Ultimate")
                .price(BigDecimal.TEN)
                .currency("TRY")
                .validityDays(30)
                .trialDays(30)
                .active(true)
                .trialEligible(true)
                .systemManaged(false)
                .items(new ArrayList<>(List.of(item)))
                .build();
    }
}
