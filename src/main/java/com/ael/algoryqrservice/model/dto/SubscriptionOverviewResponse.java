package com.ael.algoryqrservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SubscriptionOverviewResponse {

    private PurchaseSummaryResponse activePackage;
    private List<UserEntitlementResponse> entitlements;
    private List<PurchaseSummaryResponse> addonPurchases;
    private BranchDtos.Quota branchQuota;
    private BranchDtos.MenuQuota menuQuota;
    private List<FulfillmentDetailResponse> fulfillmentDetails;
    private boolean fulfillmentActive;
}
