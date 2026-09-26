package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.dto.AccountOverviewDtos;
import com.ael.algoryqrservice.model.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
import com.ael.algoryqrservice.model.dto.SessionPageResponse;
import com.ael.algoryqrservice.model.dto.SessionResponse;
import com.ael.algoryqrservice.model.dto.SubscriptionOverviewResponse;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class AccountFacadeService {

    private final AccountService accountService;
    private final BillingAddressService billingAddressService;
    private final PaymentServiceClient paymentServiceClient;
    private final PurchaseService purchaseService;
    private final ExternalPackageViewService externalPackageView;
    private final AuthService authService;
    private final SecurityUtils securityUtils;

    public AccountDtos.MyProfileResponse getMyProfile() {
        return accountService.getMyProfile();
    }

    public AccountDtos.MyProfileResponse updateMyProfile(AccountDtos.MyProfilePatchRequest request) {
        return accountService.updateMyProfile(request);
    }

    public BillingAddressPageResponse listBillingAddresses(int page, int size) {
        return billingAddressService.list(userId(), page, size);
    }

    public List<BillingPaymentDtos.PaymentMethod> listPaymentMethods() {
        return paymentServiceClient.getPaymentMethods(userId());
    }

    public List<PurchaseResponse> listPurchases() {
        Long userId = userId();
        return externalPackageView.purchases(userId)
                .orElseGet(() -> purchaseService.getUserPurchases(userId));
    }

    public SubscriptionOverviewResponse subscriptionOverview() {
        Long userId = userId();
        return externalPackageView.overview(userId)
                .orElseGet(() -> purchaseService.getMySubscriptionOverview(userId));
    }

    public SessionPageResponse listSessions(String accessToken, int page, int size) {
        return authService.getMySessions(accessToken, page, size);
    }

    public void revokeSession(java.util.UUID sessionId) {
        authService.revokeSession(sessionId);
    }

    public AccountOverviewDtos.OverviewResponse getOverview(String accessToken, int sessionPage, int sessionSize) {
        int safeSessionPage = Math.max(sessionPage, 0);
        int safeSessionSize = Math.min(Math.max(sessionSize, 1), AccountOverviewDtos.DEFAULT_SESSION_PAGE_SIZE);

        CompletableFuture<AccountOverviewDtos.Section<AccountDtos.MyProfileResponse>> profileFuture =
                CompletableFuture.supplyAsync(() -> sectionFromCall(() -> accountService.getMyProfile()));
        CompletableFuture<AccountOverviewDtos.Section<SubscriptionOverviewResponse>> subscriptionFuture =
                CompletableFuture.supplyAsync(this::subscriptionOverviewSection);
        CompletableFuture<AccountOverviewDtos.Section<List<PurchaseResponse>>> purchasesFuture =
                CompletableFuture.supplyAsync(this::purchasesSection);
        CompletableFuture<AccountOverviewDtos.Section<List<BillingPaymentDtos.PaymentMethod>>> paymentMethodsFuture =
                CompletableFuture.supplyAsync(this::paymentMethodsSection);
        CompletableFuture<AccountOverviewDtos.Section<BillingAddressPageResponse>> billingAddressesFuture =
                CompletableFuture.supplyAsync(() -> sectionFromCall(
                        () -> billingAddressService.list(userId(), 0, AccountOverviewDtos.DEFAULT_SESSION_PAGE_SIZE)
                ));
        CompletableFuture<AccountOverviewDtos.Section<AccountOverviewDtos.SessionsBundle>> sessionsFuture =
                CompletableFuture.supplyAsync(() -> sessionsBundleSection(accessToken, safeSessionPage, safeSessionSize));

        CompletableFuture.allOf(
                profileFuture,
                subscriptionFuture,
                purchasesFuture,
                paymentMethodsFuture,
                billingAddressesFuture,
                sessionsFuture
        ).join();

        return AccountOverviewDtos.OverviewResponse.builder()
                .profile(profileFuture.join())
                .subscription(subscriptionFuture.join())
                .purchases(purchasesFuture.join())
                .paymentMethods(paymentMethodsFuture.join())
                .billingAddresses(billingAddressesFuture.join())
                .sessions(sessionsFuture.join())
                .build();
    }

    private AccountOverviewDtos.Section<SubscriptionOverviewResponse> subscriptionOverviewSection() {
        return sectionFromCall(this::subscriptionOverview);
    }

    private AccountOverviewDtos.Section<List<PurchaseResponse>> purchasesSection() {
        return sectionFromCall(this::listPurchases);
    }

    private AccountOverviewDtos.Section<List<BillingPaymentDtos.PaymentMethod>> paymentMethodsSection() {
        return sectionFromCall(this::listPaymentMethods);
    }

    private AccountOverviewDtos.Section<AccountOverviewDtos.SessionsBundle> sessionsBundleSection(
            String accessToken,
            int page,
            int size
    ) {
        return sectionFromCall(() -> {
            SessionPageResponse sessionPage = authService.getMySessions(accessToken, page, size);
            List<SessionResponse> openSessions = sessionPage.getContent() == null
                    ? List.of()
                    : sessionPage.getContent().stream().filter(this::isOpenSession).toList();
            return AccountOverviewDtos.SessionsBundle.builder()
                    .page(sessionPage)
                    .openSessions(openSessions)
                    .build();
        });
    }

    private boolean isOpenSession(SessionResponse session) {
        return session != null && session.isActive() && !session.isRevoked() && !session.isExpired();
    }

    private <T> AccountOverviewDtos.Section<T> sectionFromCall(SupplierWithException<T> supplier) {
        try {
            return AccountOverviewDtos.Section.success(supplier.get());
        } catch (PaymentServiceException ex) {
            return AccountOverviewDtos.Section.failure(502, ex.getMessage());
        } catch (RestClientResponseException ex) {
            return AccountOverviewDtos.Section.failure(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (CompletionException ex) {
            return mapThrowable(ex.getCause());
        } catch (RuntimeException ex) {
            return mapThrowable(ex);
        }
    }

    private <T> AccountOverviewDtos.Section<T> mapThrowable(Throwable throwable) {
        if (throwable instanceof PaymentServiceException paymentEx) {
            return AccountOverviewDtos.Section.failure(502, paymentEx.getMessage());
        }
        if (throwable instanceof RestClientResponseException restEx) {
            return AccountOverviewDtos.Section.failure(restEx.getStatusCode().value(), restEx.getResponseBodyAsString());
        }
        String message = throwable != null && throwable.getMessage() != null
                ? throwable.getMessage()
                : "Sunucu hatası";
        return AccountOverviewDtos.Section.failure(500, message);
    }

    private Long userId() {
        return securityUtils.getCurrentUserId();
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
