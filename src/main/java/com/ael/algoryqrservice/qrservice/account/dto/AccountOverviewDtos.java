package com.ael.algoryqrservice.qrservice.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.ael.algoryqrservice.qrservice.billing.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.PurchaseResponse;
import com.ael.algoryqrservice.qrservice.billing.dto.SubscriptionOverviewResponse;

public final class AccountOverviewDtos {

    private AccountOverviewDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section<T> {
        private boolean ok;
        private T data;
        private Integer status;
        private String message;

        public static <T> Section<T> success(T data) {
            return Section.<T>builder().ok(true).data(data).build();
        }

        public static <T> Section<T> failure(int status, String message) {
            return Section.<T>builder()
                    .ok(false)
                    .status(status)
                    .message(message != null && !message.isBlank() ? message : "İstek başarısız")
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewResponse {
        private Section<AccountDtos.MyProfileResponse> profile;
        private Section<SubscriptionOverviewResponse> subscription;
        private Section<List<PurchaseResponse>> purchases;
        private Section<BillingAddressPageResponse> billingAddresses;
    }
}
