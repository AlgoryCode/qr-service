package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.AccountDtos;
import com.ael.algoryqrservice.model.dto.AccountOverviewDtos;
import com.ael.algoryqrservice.model.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.model.dto.PurchaseResponse;
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
    private final PurchaseService purchaseService;
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

    public List<PurchaseResponse> listPurchases() {
        return purchaseService.getUserPurchases(userId());
    }

    public SubscriptionOverviewResponse subscriptionOverview() {
        return purchaseService.getMySubscriptionOverview(userId());
    }

    public AccountOverviewDtos.OverviewResponse getOverview() {
        CompletableFuture<AccountOverviewDtos.Section<AccountDtos.MyProfileResponse>> profileFuture =
                CompletableFuture.supplyAsync(() -> sectionFromCall(() -> accountService.getMyProfile()));
        CompletableFuture<AccountOverviewDtos.Section<SubscriptionOverviewResponse>> subscriptionFuture =
                CompletableFuture.supplyAsync(this::subscriptionOverviewSection);
        CompletableFuture<AccountOverviewDtos.Section<List<PurchaseResponse>>> purchasesFuture =
                CompletableFuture.supplyAsync(this::purchasesSection);
        CompletableFuture<AccountOverviewDtos.Section<BillingAddressPageResponse>> billingAddressesFuture =
                CompletableFuture.supplyAsync(() -> sectionFromCall(
                        () -> billingAddressService.list(userId(), 0, 50)
                ));

        CompletableFuture.allOf(
                profileFuture,
                subscriptionFuture,
                purchasesFuture,
                billingAddressesFuture
        ).join();

        return AccountOverviewDtos.OverviewResponse.builder()
                .profile(profileFuture.join())
                .subscription(subscriptionFuture.join())
                .purchases(purchasesFuture.join())
                .billingAddresses(billingAddressesFuture.join())
                .build();
    }

    private AccountOverviewDtos.Section<SubscriptionOverviewResponse> subscriptionOverviewSection() {
        return sectionFromCall(this::subscriptionOverview);
    }

    private AccountOverviewDtos.Section<List<PurchaseResponse>> purchasesSection() {
        return sectionFromCall(this::listPurchases);
    }

    private <T> AccountOverviewDtos.Section<T> sectionFromCall(SupplierWithException<T> supplier) {
        try {
            return AccountOverviewDtos.Section.success(supplier.get());
        } catch (RestClientResponseException ex) {
            return AccountOverviewDtos.Section.failure(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (CompletionException ex) {
            return mapThrowable(ex.getCause());
        } catch (RuntimeException ex) {
            return mapThrowable(ex);
        }
    }

    private <T> AccountOverviewDtos.Section<T> mapThrowable(Throwable throwable) {
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
