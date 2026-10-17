package com.ael.algoryqrservice.fulfillmentservice.presentation.mapper;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackageView;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PlanPackageItem;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductAccessView;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ActivePackageResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ConsumeEntitlementResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.EntitlementResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.FulfillmentResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.PackageItemResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.PackageResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ProductAccessResponse;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentPresentationMapper {

    public ActivePackageResponse toActivePackageResponse(ActivePackageView view) {
        return new ActivePackageResponse(
                view.id(),
                view.userId(),
                view.fulfillmentId(),
                view.purchaseId(),
                view.packageId(),
                view.packageCode(),
                view.packageName(),
                view.periodStart(),
                view.periodEnd(),
                view.status(),
                view.activatedAt(),
                view.updatedAt(),
                view.products(),
                view.scopes()
        );
    }

    public FulfillmentResponse toFulfillmentResponse(PackageFulfillment fulfillment) {
        return new FulfillmentResponse(
                fulfillment.id(),
                fulfillment.eventId(),
                fulfillment.eventType(),
                fulfillment.purchaseId(),
                fulfillment.userId(),
                fulfillment.packageId(),
                fulfillment.packageCode(),
                fulfillment.conversationId(),
                fulfillment.paymentId(),
                fulfillment.subscriptionId(),
                fulfillment.billingCycleNumber(),
                fulfillment.periodStart(),
                fulfillment.periodEnd(),
                fulfillment.amount(),
                fulfillment.currency(),
                fulfillment.status(),
                fulfillment.createdAt(),
                fulfillment.updatedAt()
        );
    }

    public PackageResponse toPackageResponse(PlanPackage planPackage) {
        return new PackageResponse(
                planPackage.id(),
                planPackage.code(),
                planPackage.name(),
                planPackage.description(),
                planPackage.kind(),
                planPackage.features(),
                planPackage.price(),
                planPackage.yearlyPrice(),
                planPackage.subtotal(),
                planPackage.vatAmount(),
                planPackage.currency(),
                planPackage.active(),
                planPackage.validityDays(),
                planPackage.priority(),
                planPackage.purchasable(),
                planPackage.items().stream().map(this::toPackageItemResponse).toList()
        );
    }

    public PackageItemResponse toPackageItemResponse(PlanPackageItem item) {
        return new PackageItemResponse(
                item.productId(),
                item.productCode(),
                item.featureCode(),
                item.scopeCode(),
                item.productType(),
                item.quantity(),
                item.unlimited()
        );
    }

    public EntitlementResponse toEntitlementResponse(UserEntitlement entitlement, String productCode) {
        return new EntitlementResponse(
                entitlement.id(),
                entitlement.userId(),
                entitlement.fulfillmentId(),
                entitlement.purchaseId(),
                entitlement.productId(),
                entitlement.productTypeId(),
                entitlement.featureCode(),
                entitlement.scopeCode(),
                entitlement.quantity(),
                entitlement.unlimited(),
                entitlement.usedQuantity(),
                entitlement.source(),
                entitlement.status(),
                entitlement.startsAt(),
                entitlement.expiresAt(),
                productCode
        );
    }

    public ProductAccessResponse toProductAccess(ProductAccessView view) {
        return new ProductAccessResponse(view.allowed(), view.packageCode(), view.featureCode(), view.scopeCode());
    }

    public ConsumeEntitlementResponse toConsume(UserEntitlement entitlement) {
        int used = entitlement.usedQuantity() == null ? 0 : entitlement.usedQuantity();
        int limit = entitlement.quantity() == null ? 0 : entitlement.quantity();
        int remaining = entitlement.unlimited() ? limit : Math.max(0, limit - used);
        return new ConsumeEntitlementResponse(
                entitlement.id(),
                entitlement.purchaseId(),
                used,
                remaining,
                entitlement.unlimited()
        );
    }

    public ConsumeEntitlementResponse emptyConsume() {
        return new ConsumeEntitlementResponse(null, null, 0, 0, false);
    }
}
