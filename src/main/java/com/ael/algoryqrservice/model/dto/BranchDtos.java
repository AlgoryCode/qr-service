package com.ael.algoryqrservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class BranchDtos {

    private BranchDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank
        @Size(max = 255)
        private String name;
        @Size(max = 1000)
        private String address;
        @Size(max = 64)
        private String phone;
        @Email
        @Size(max = 255)
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255)
        private String name;
        @Size(max = 1000)
        private String address;
        @Size(max = 64)
        private String phone;
        @Email
        @Size(max = 255)
        private String email;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuSummary {
        private Long menuId;
        private Long qrId;
        private String businessName;
        private boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long userId;
        private String name;
        private String address;
        private String phone;
        private String email;
        private String photoUrl;
        private boolean grandfathered;
        private boolean active;
        private List<MenuSummary> menus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Quota {
        private int used;
        private int allowed;
        private int remaining;
        private int grandfathered;
        private int extraPurchased;
        private boolean canCreate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuQuota {
        private int extraUsed;
        private int extraAllowed;
        private int extraRemaining;
        private boolean canCreateExtra;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<Response> content;
        private Quota quota;
        private MenuQuota menuQuota;
    }
}
