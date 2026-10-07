package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.PackageView;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.SessionContextResponse;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

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
    private ObjectProvider<FulfillmentServiceClient> fulfillmentClients;
    @Mock
    private FulfillmentServiceClient fulfillmentClient;

    private SessionContextService service;

    @BeforeEach
    void setUp() {
        service = new SessionContextService(userRepository, fulfillmentClients, new SessionContextAssembler());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(fulfillmentClients.getIfAvailable()).thenReturn(fulfillmentClient);
    }

    @Test
    void resolve_whenPackageActive_thenAllow() {
        when(fulfillmentClient.findPackage(USER_ID)).thenReturn(new PackageView("ACTIVE", activePackage("ACTIVE")));
        when(fulfillmentClient.listEntitlements(USER_ID)).thenReturn(List.of(entitlement()));

        SessionContextResponse response = service.resolve(USER_ID);

        assertThat(response.decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(response.packageCode()).isEqualTo("ULTIMATE_PACKAGE");
        assertThat(response.packageName()).isEqualTo("Ultimate");
        assertThat(response.products()).containsExactly("QR_MENU");
        assertThat(response.scopes()).containsExactly("QR_MENU_OWNER");
    }

    @Test
    void resolve_whenPackageInactive_thenRequirePurchase() {
        when(fulfillmentClient.findPackage(USER_ID)).thenReturn(new PackageView("INACTIVE", null));

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
}
