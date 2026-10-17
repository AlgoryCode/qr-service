package com.ael.algoryqrservice.qrservice.account.service;

import com.ael.algoryqrservice.qrservice.account.dto.AccountDtos;
import com.ael.algoryqrservice.qrservice.account.dto.AccountOverviewDtos;
import com.ael.algoryqrservice.qrservice.billing.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.SubscriptionOverviewResponse;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import com.ael.algoryqrservice.qrservice.billing.service.BillingAddressService;
import com.ael.algoryqrservice.qrservice.billing.service.PurchaseService;
import com.ael.algoryqrservice.qrservice.account.service.AccountService;

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
        return billingAddressService.list(merchantId(), page, size);
    }

    public List<PurchaseResponse> listPurchases() {
        return purchaseService.getUserPurchases(merchantId());
    }

    public SubscriptionOverviewResponse subscriptionOverview() {
        return purchaseService.getMySubscriptionOverview(merchantId());
    }

    public AccountOverviewDtos.OverviewResponse getOverview() {
        return AccountOverviewDtos.OverviewResponse.builder()
                .profile(sectionFromCall(() -> accountService.getMyProfile()))
                .subscription(subscriptionOverviewSection())
                .purchases(purchasesSection())
                .billingAddresses(sectionFromCall(() -> billingAddressService.list(merchantId(), 0, 50)))
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

    private Long merchantId() {
        return securityUtils.getCurrentMerchantId();
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
