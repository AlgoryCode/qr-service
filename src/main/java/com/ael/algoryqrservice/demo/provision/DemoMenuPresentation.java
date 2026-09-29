package com.ael.algoryqrservice.demo.provision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DemoMenuPresentation(
        String branchName,
        String menuBusinessName,
        String slogan,
        String chefName,
        String chefAvatarKey,
        String phone,
        String email,
        String address,
        String logoUrl,
        String logoKey,
        String coverUrl,
        String coverKey,
        List<String> preferredThemeCodes
) {
    public static DemoMenuPresentation defaults() {
        return new DemoMenuPresentation(
                DemoProvisionContext.BRANCH_NAME,
                DemoProvisionContext.MENU_BUSINESS_NAME,
                "AlgoryQR deneme menüsü",
                "Algory Demo Şef",
                null,
                "+90 212 555 01 00",
                "demo@algoryqr.com",
                "İstanbul, Türkiye",
                null,
                null,
                null,
                null,
                List.of("luxury")
        );
    }
}
