package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.billing.policy.PlanPackageAssignmentPolicy;
import com.ael.algoryqrservice.qrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.PackageView;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ConflictException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import com.ael.algoryqrservice.qrservice.billing.service.UserDebtService;

@Service
@RequiredArgsConstructor
public class UserPackageAssignmentService {

    public static final String ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
    public static final String PACKAGE_EXISTS = "PACKAGE_EXISTS";
    public static final String ACCOUNT_IN_DEBT = "ACCOUNT_IN_DEBT";
    public static final String PACKAGE_PRICE_MISMATCH = "PACKAGE_PRICE_MISMATCH";

    private static final String ACTIVE = "ACTIVE";
    private final FulfillmentServiceClient fulfillmentServiceClient;
    private final AuthMerchantClient authMerchantClient;
    private final UserDebtService userDebtService;
    private final PlanPackageRepository planPackageRepository;
    private final PlanPackageAssignmentPolicy assignmentPolicy;

    public PackageControl read(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            throw new BadRequestException("İşletme geçersiz");
        }
        FulfillmentServiceClient fulfillment = fulfillmentServiceClient;
        PackageView view = fulfillment.findPackage(merchantId);
        if (view.body() == null) {
            return new PackageControl("NONE", null, null);
        }
        String periodEnd = view.body().periodEnd() == null ? null : view.body().periodEnd().toString();
        List<PackageControl.PackageRight> entitlements = fulfillment.listEntitlements(merchantId).stream()
                .map(this::right)
                .toList();
        return new PackageControl(view.status(), view.body().packageCode(), periodEnd, entitlements);
    }

    private PackageControl.PackageRight right(ExternalEntitlementResponse item) {
        return new PackageControl.PackageRight(
                item.productCode() != null ? item.productCode() : item.featureCode(),
                item.featureCode(),
                item.quantity(),
                item.usedQuantity(),
                item.unlimited()
        );
    }

    public void create(Long merchantId, Long packageId, String packageCode) {
        if (merchantId == null || merchantId <= 0) {
            throw new BadRequestException("İşletme geçersiz");
        }
        AuthMerchantClient.MerchantView merchant = authMerchantClient.findMerchant(merchantId);
        if (!ACTIVE.equals(merchant.status())) {
            throw new BadRequestException(ACCOUNT_INACTIVE, "Hesap aktif değil");
        }
        PackageView current = fulfillmentServiceClient.findPackage(merchantId);
        if (ACTIVE.equals(current.status())) {
            throw new ConflictException(PACKAGE_EXISTS, "Aktif paket mevcut");
        }
        if (userDebtService.findDebt(merchantId).isPresent()) {
            throw new BadRequestException(ACCOUNT_IN_DEBT, "Hesap borcu var");
        }
        Long resolvedId = resolvePackageId(packageId, packageCode);
        PlanPackage plan = planPackageRepository.findByIdWithItems(resolvedId)
                .orElseThrow(() -> new NotFoundException("Paket bulunamadı"));
        assignmentPolicy.requireAssignable(plan);
        int periodDays = assignmentPolicy.resolvePeriodDays(plan, plan.getValidityDays(), plan.getValidityDays());
        fulfillmentServiceClient.createPackage(merchantId, plan.getCode(), periodDays);
    }

    private Long resolvePackageId(Long packageId, String packageCode) {
        if (packageId != null) {
            return packageId;
        }
        if (packageCode == null || packageCode.isBlank()) {
            throw new BadRequestException("Paket zorunludur");
        }
        return planPackageRepository.findByCode(packageCode.trim())
                .map(PlanPackage::getId)
                .orElseThrow(() -> new NotFoundException("Paket bulunamadı"));
    }

    public record PackageControl(
            String status,
            String packageCode,
            String periodEnd,
            List<PackageRight> entitlements
    ) {
        public PackageControl(String status, String packageCode, String periodEnd) {
            this(status, packageCode, periodEnd, List.of());
        }

        public record PackageRight(
                String productCode,
                String featureCode,
                Integer quantity,
                Integer usedQuantity,
                boolean unlimited
        ) {
        }
    }

}
