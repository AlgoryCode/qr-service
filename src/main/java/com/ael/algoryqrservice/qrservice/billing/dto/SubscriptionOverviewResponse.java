package com.ael.algoryqrservice.qrservice.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import com.ael.algoryqrservice.qrservice.branch.dto.BranchDtos;
import com.ael.algoryqrservice.qrservice.integration.dto.FulfillmentDetailResponse;

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
