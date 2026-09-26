package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.dto.AccountOverviewDtos;
import com.ael.algoryqrservice.model.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.SessionPageResponse;
import com.ael.algoryqrservice.model.dto.SessionResponse;
import com.ael.algoryqrservice.model.dto.SubscriptionOverviewResponse;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountFacadeServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private BillingAddressService billingAddressService;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private PurchaseService purchaseService;
    @Mock
    private ExternalPackageViewService externalPackageView;
    @Mock
    private AuthService authService;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private AccountFacadeService accountFacadeService;

    @Test
    void getOverviewAggregatesAllSections() {
        when(securityUtils.getCurrentUserId()).thenReturn(42L);
        when(accountService.getMyProfile()).thenReturn(
                AccountDtos.MyProfileResponse.builder().userId(42L).email("a@b.com").build()
        );
        when(externalPackageView.overview(42L)).thenReturn(Optional.of(SubscriptionOverviewResponse.builder().build()));
        when(externalPackageView.purchases(42L)).thenReturn(Optional.empty());
        when(purchaseService.getUserPurchases(42L)).thenReturn(List.of());
        when(paymentServiceClient.getPaymentMethods(42L)).thenReturn(List.of(
                new BillingPaymentDtos.PaymentMethod("1", null, null, "4242", null, null)
        ));
        when(billingAddressService.list(eq(42L), eq(0), anyInt()))
                .thenReturn(BillingAddressPageResponse.builder().content(List.of()).build());
        UUID current = UUID.randomUUID();
        when(authService.getMySessions("token", 0, 50)).thenReturn(SessionPageResponse.builder()
                .content(List.of(
                        SessionResponse.builder()
                                .sessionId(current)
                                .active(true)
                                .revoked(false)
                                .expired(false)
                                .current(true)
                                .build(),
                        SessionResponse.builder()
                                .sessionId(UUID.randomUUID())
                                .active(true)
                                .revoked(true)
                                .expired(false)
                                .current(false)
                                .build()
                ))
                .build());

        AccountOverviewDtos.OverviewResponse overview = accountFacadeService.getOverview("token", 0, 50);

        assertThat(overview.getProfile().isOk()).isTrue();
        assertThat(overview.getProfile().getData().getEmail()).isEqualTo("a@b.com");
        assertThat(overview.getPaymentMethods().isOk()).isTrue();
        assertThat(overview.getPaymentMethods().getData()).hasSize(1);
        assertThat(overview.getSessions().isOk()).isTrue();
        assertThat(overview.getSessions().getData().getOpenSessions()).hasSize(1);
        assertThat(overview.getSessions().getData().getOpenSessions().getFirst().getSessionId()).isEqualTo(current);

        verify(paymentServiceClient).getPaymentMethods(42L);
        verify(authService).getMySessions("token", 0, 50);
    }
}
