package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.access.AccessSession;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.client.ActivePackageLookup;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.model.FulfillmentDetail;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.SessionContextResponse;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.FulfillmentDetailSource;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.FulfillmentDetailRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionContextServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private FulfillmentDetailRepository fulfillmentDetailRepository;
    @Mock
    private SessionAccessService sessionAccessService;
    @Mock
    private ObjectProvider<FulfillmentServiceClient> fulfillmentClients;
    @Mock
    private FulfillmentServiceClient fulfillmentClient;

    private SessionContextService service;

    @BeforeEach
    void setUp() {
        service = new SessionContextService(
                userRepository,
                fulfillmentDetailRepository,
                sessionAccessService,
                fulfillmentClients,
                new SessionContextAssembler()
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(fulfillmentClients.getIfAvailable()).thenReturn(fulfillmentClient);
    }

    @Test
    void resolve_whenFulfillmentPackageActive_thenAllowWithRemoteProducts() {
        when(fulfillmentClient.lookupActivePackage(USER_ID)).thenReturn(new ActivePackageLookup.Found(activePackage("ACTIVE")));
        when(fulfillmentClient.listEntitlements(USER_ID)).thenReturn(List.of(entitlement()));

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(response.packageCode()).isEqualTo("ULTIMATE_PACKAGE");
        assertThat(response.packageName()).isEqualTo("Ultimate");
        assertThat(response.products()).containsExactly("QR_MENU");
        assertThat(response.scopes()).containsExactly("QR_MENU_OWNER");
        assertThat(response.entitlements()).singleElement().satisfies(item -> {
            assertThat(item.featureCode()).isEqualTo("QR_MENU");
            assertThat(item.quantity()).isEqualTo(10);
            assertThat(item.usedQuantity()).isEqualTo(2);
            assertThat(item.source()).isEqualTo("PACKAGE_INCLUDE");
        });
        assertThat(response.user().email()).isEqualTo("ada@example.com");
        assertThat(response.endsAt()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay());
        verify(sessionAccessService, never()).resolve(any());
    }

    @Test
    void resolve_whenFulfillmentMissingAndTrialAllow_thenLocalProducts() {
        when(fulfillmentClient.lookupActivePackage(USER_ID)).thenReturn(new ActivePackageLookup.Absent());
        when(sessionAccessService.resolve(USER_ID)).thenReturn(
                AccessSession.of(AccessDecision.ALLOW, "ULTIMATE_TRIAL_PACKAGE", LocalDateTime.of(2026, 10, 1, 0, 0), null)
        );
        when(fulfillmentDetailRepository.findAllActiveByUserId(eq(USER_ID), any())).thenReturn(List.of(trialDetail()));

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(response.packageCode()).isEqualTo("ULTIMATE_TRIAL_PACKAGE");
        assertThat(response.products()).containsExactly("QR_MENU");
        assertThat(response.scopes()).containsExactly("QR_MENU_OWNER");
        assertThat(response.entitlements()).singleElement().satisfies(item ->
                assertThat(item.source()).isEqualTo("ONBOARDING_PACKAGE")
        );
        verify(fulfillmentClient, never()).listEntitlements(any());
    }

    @Test
    void resolve_whenFulfillmentMissingAndNewUser_thenStartPackage() {
        assertBlocked(AccessDecision.START_PACKAGE, null, null);
    }

    @Test
    void resolve_whenFulfillmentMissingAndExpired_thenRequirePurchase() {
        assertBlocked(AccessDecision.REQUIRE_PURCHASE, null, null);
    }

    @Test
    void resolve_whenLocalPurchaseInDebt_thenRequirePayment() {
        LocalDateTime debtDueAt = LocalDateTime.of(2026, 9, 30, 12, 0);
        assertBlocked(AccessDecision.REQUIRE_PAYMENT, "PRO_PACKAGE", debtDueAt);
    }

    @Test
    void resolve_whenFulfillmentUnavailable_thenFailWithoutDecision() {
        when(fulfillmentClient.lookupActivePackage(USER_ID))
                .thenThrow(new FulfillmentUnavailableException("Paket bilgisi şu an alınamıyor"));

        assertThatThrownBy(() -> service.resolve(USER_ID))
                .isInstanceOf(FulfillmentUnavailableException.class);
        verify(sessionAccessService, never()).resolve(any());
    }

    @Test
    void resolve_whenFulfillmentDisabledAndAllow_thenLocalProducts() {
        when(fulfillmentClients.getIfAvailable()).thenReturn(null);
        when(sessionAccessService.resolve(USER_ID)).thenReturn(
                AccessSession.of(AccessDecision.ALLOW, "PRO_PACKAGE", LocalDateTime.of(2026, 11, 1, 0, 0), null)
        );
        when(fulfillmentDetailRepository.findAllActiveByUserId(eq(USER_ID), any())).thenReturn(List.of(trialDetail()));

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(response.packageCode()).isEqualTo("PRO_PACKAGE");
        assertThat(response.products()).containsExactly("QR_MENU");
        verify(fulfillmentClient, never()).lookupActivePackage(any());
    }

    private void assertBlocked(AccessDecision decision, String packageCode, LocalDateTime debtDueAt) {
        when(fulfillmentClient.lookupActivePackage(USER_ID)).thenReturn(new ActivePackageLookup.Absent());
        when(sessionAccessService.resolve(USER_ID)).thenReturn(AccessSession.of(decision, packageCode, null, debtDueAt));

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(decision);
        assertThat(response.packageCode()).isEqualTo(packageCode);
        assertThat(response.debtDueAt()).isEqualTo(debtDueAt);
        assertThat(response.products()).isEmpty();
        assertThat(response.scopes()).isEmpty();
        assertThat(response.entitlements()).isEmpty();
        verify(fulfillmentDetailRepository, never()).findAllActiveByUserId(any(), any());
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
                LocalDate.of(2026, 10, 1),
                status,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"),
                List.of("QR_MENU"),
                List.of("QR_MENU_OWNER")
        );
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

    private FulfillmentDetail trialDetail() {
        return FulfillmentDetail.builder()
                .featureCode("QR_MENU")
                .scopeCode("QR_MENU_OWNER")
                .quantity(3)
                .unlimited(false)
                .usedQuantity(1)
                .source(FulfillmentDetailSource.ONBOARDING_PACKAGE)
                .build();
    }
}
