package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SubscriptionOverviewResponse {

    private PurchaseSummaryResponse activePackage;
    private List<UserEntitlementResponse> entitlements;
}
