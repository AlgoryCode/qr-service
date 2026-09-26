package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class AccountOverviewDtos {

    public static final int DEFAULT_SESSION_PAGE_SIZE = 50;

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
    public static class SessionsBundle {
        private SessionPageResponse page;
        private List<SessionResponse> openSessions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewResponse {
        private Section<AccountDtos.MyProfileResponse> profile;
        private Section<SubscriptionOverviewResponse> subscription;
        private Section<List<PurchaseResponse>> purchases;
        private Section<List<BillingPaymentDtos.PaymentMethod>> paymentMethods;
        private Section<BillingAddressPageResponse> billingAddresses;
        private Section<SessionsBundle> sessions;
    }
}
