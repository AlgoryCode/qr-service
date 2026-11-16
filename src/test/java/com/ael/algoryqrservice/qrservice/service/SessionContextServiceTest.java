package com.ael.algoryqrservice.qrservice.service;

import com.ael.algoryqrservice.qrservice.account.service.SessionContextAssembler;

import com.ael.algoryqrservice.qrservice.account.service.SessionContextService;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.PackageView;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.qrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.qrservice.account.dto.SessionContextResponse;
import com.ael.algoryqrservice.qrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.qrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.qrservice.model.enums.UserRole;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.access.BillingState;
import com.ael.algoryqrservice.qrservice.access.SessionAccessService;
import com.ael.algoryqrservice.qrservice.billing.externalpackageresponsemapper.service.ExternalPackageResponseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionContextServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private com.ael.algoryqrservice.qrservice.client.AuthMerchantClient authMerchantClient;
    @Mock
    private FulfillmentServiceClient fulfillmentClient;

    private SessionContextService service;

    @BeforeEach
    void setUp() {
        ExternalPackageResponseMapper mapper = new ExternalPackageResponseMapper();
        service = new SessionContextService(
                userRepository,
                authMerchantClient,
                fulfillmentClient,
                new SessionContextAssembler(),
                new SessionAccessService(fulfillmentClient, mapper),
                mapper);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
    }

    @Test
    void resolve_whenPackageActive_thenAllow() {
        when(fulfillmentClient.findPackage(USER_ID)).thenReturn(new PackageView("ACTIVE", activePackage("ACTIVE")));
        when(fulfillmentClient.listEntitlements(USER_ID)).thenReturn(List.of(entitlement()));

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(response.billingState()).isEqualTo(BillingState.ACTIVE);
        assertThat(response.packageCode()).isEqualTo("ULTIMATE_PACKAGE");
        assertThat(response.packageName()).isEqualTo("Ultimate");
        assertThat(response.products()).containsExactly("QR_MENU");
        assertThat(response.scopes()).containsExactly("QR_MENU_OWNER");
    }

    @Test
    void resolve_whenRenewalFailed_thenAllowWithGraceDeadline() {
        Instant graceEndsAt = Instant.now().plusSeconds(3 * 86_400);
        ExternalActivePackageResponse grace = withLifecycle(activePackage("GRACE_PERIOD"), "PAID", graceEndsAt,
                LocalDate.now().minusDays(1));
        when(fulfillmentClient.findPackage(USER_ID)).thenReturn(new PackageView("ACTIVE", grace));
        when(fulfillmentClient.listEntitlements(USER_ID)).thenReturn(List.of(entitlement()));

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(response.billingState()).isEqualTo(BillingState.PAST_DUE);
        assertThat(response.debtDueAt()).isNotNull();
        assertThat(response.entitlements().getFirst().remaining()).isEqualTo(8);
    }

    @Test
    void resolve_whenTrial_thenTrialing() {
        when(fulfillmentClient.findPackage(USER_ID)).thenReturn(new PackageView("ACTIVE",
                withLifecycle(activePackage("ACTIVE"), "TRIAL", null, LocalDate.now().plusDays(5))));
        when(fulfillmentClient.listEntitlements(USER_ID)).thenReturn(List.of());

        assertThat(service.resolve(USER_ID).billingState()).isEqualTo(BillingState.TRIALING);
    }

    @Test
    void resolve_whenNeverHadPackage_thenStartPackage() {
        when(fulfillmentClient.findPackage(USER_ID)).thenReturn(new PackageView("INACTIVE", null));
        when(fulfillmentClient.hasAnyPackage(USER_ID)).thenReturn(false);

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(AccessDecision.START_PACKAGE);
        assertThat(response.billingState()).isEqualTo(BillingState.NONE);
    }

    @Test
    void resolve_whenPackageInactive_thenRequirePurchase() {
        when(fulfillmentClient.findPackage(USER_ID)).thenReturn(new PackageView("INACTIVE", null));
        when(fulfillmentClient.hasAnyPackage(USER_ID)).thenReturn(true);

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);
        verify(fulfillmentClient, never()).listEntitlements(any());
    }

    @Test
    void resolve_whenFulfillmentUnavailable_thenFail() {
        when(fulfillmentClient.findPackage(USER_ID))
                .thenThrow(new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor"));

        assertThatThrownBy(() -> service.resolve(USER_ID))
                .isInstanceOf(FulfillmentUnavailableException.class);
    }

    private User user() {
        return User.builder()
                .id(USER_ID)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .phone("555")
                .provider(AuthProvider.BASIC)
                .role(UserRole.USER)
                .build();
    }

    private ExternalActivePackageResponse activePackage(String status) {
        return new ExternalActivePackageResponse(
                1L,
                USER_ID,
                2L,
                3L,
                4L,
                "ULTIMATE_PACKAGE",
                "Ultimate",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2099, 10, 1),
                status,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"),
                List.of("QR_MENU"),
                List.of("QR_MENU_OWNER")
        );
    }

    private static ExternalActivePackageResponse withLifecycle(
            ExternalActivePackageResponse base, String kind, Instant graceEndsAt, LocalDate periodEnd) {
        return new ExternalActivePackageResponse(base.id(), base.merchantId(), base.fulfillmentId(), base.purchaseId(),
                base.packageId(), base.packageCode(), base.packageName(), base.periodStart(), periodEnd,
                base.status(), base.activatedAt(), base.updatedAt(), base.products(), base.scopes(), kind, graceEndsAt);
    }

    private ExternalEntitlementResponse entitlement() {
        return new ExternalEntitlementResponse(
                9L,
                USER_ID,
                2L,
                3L,
                5L,
                "FEATURE",
                "QR_MENU",
                "QR_MENU_OWNER",
                10,
                false,
                2,
                "PACKAGE_INCLUDE",
                "ACTIVE",
                null,
                null,
                "QR_MENU"
        );
    }
}
