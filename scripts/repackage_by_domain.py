#!/usr/bin/env python3
"""
Move controllers, services, and DTOs under domain packages:
  com.ael.algoryqrservice.<domain>.controller|service|dto
Updates package declarations and imports under src/main/java only.
"""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java/com/ael/algoryqrservice"

CONTROLLERS: dict[str, list[str]] = {
    "common": ["HealthCheckController"],
    "account": [
        "AccountController",
        "AccessSessionController",
        "AuthEmailVerificationController",
        "CustomerAccountController",
    ],
    "menu": [
        "MenuController",
        "MenuCategoryController",
        "MenuCoverController",
        "MenuLogoController",
        "MenuTaxonomyController",
        "MenuThemeController",
        "PublicMenuThemeController",
        "AiMenuImportController",
        "FeatureAiMenuImportController",
        "InternalAiMenuImportController",
    ],
    "product": ["MenuProductIndexController", "ProductImageController"],
    "order": [
        "PublicOrderController",
        "CustomerOrderController",
        "PublicTableSessionController",
        "WaiterOrderController",
        "WaiterBillController",
    ],
    "analytics": [
        "AnalyticsController",
        "BatchReportController",
        "SiteAnalyticsController",
        "MenuFixedExpenseController",
        "FeatureSmartReportingController",
    ],
    "waiter": [
        "WaiterPanelController",
        "WaiterKitchenController",
        "WaiterCampaignController",
        "WaiterCommissionController",
        "WaiterCourierController",
        "WaiterOpsController",
    ],
    "branch": ["BranchController", "FeatureBranchController"],
    "campaign": [
        "CampaignPanelController",
        "PublicCampaignController",
        "CampaignClaimController",
    ],
    "billing": [
        "PurchaseController",
        "BillingAddressController",
        "CouponController",
        "UserAccountingController",
        "UserActivePackageController",
        "PackageController",
    ],
    "qr": ["QrController", "FeatureQrController"],
    "stock": ["StockController", "StockRecipeController"],
    "integration": ["IntegrationApprovalController"],
    "internal": [
        "InternalAiMenuImportController",
        "InternalAuthSupportController",
        "InternalIntegrationController",
        "InternalUserPackageController",
        "InternalVerificationMailController",
    ],
    "admin": [
        "AdminCouponController",
        "AdminPlatformFeedbackController",
        "AdminPurchaseController",
        "AdminSiteAnalyticsController",
        "AdminTaxonomyController",
        "AdminThemeController",
        "AdminUserController",
    ],
    "platform": ["PlatformFeedbackController"],
}

SERVICES: dict[str, list[str]] = {
    "account": [
        "AccountCredentialGateway",
        "AccountFacadeService",
        "AccountService",
        "CustomerAccountService",
        "CustomUserDetailsService",
        "EmailChangeService",
        "JwtPropertiesHelper",
        "JwtService",
        "SessionContextAssembler",
        "SessionContextService",
        "UserAccessProfileService",
    ],
    "menu": [
        "AiMenuImportService",
        "ChefAvatarService",
        "MenuCatalogCloneService",
        "MenuCategoryService",
        "MenuFeedbackService",
        "MenuPublicAccessService",
        "MenuPublicIdGenerator",
        "MenuQrSoftDeleteService",
        "MenuRatingService",
        "MenuReservationService",
        "MenuService",
        "MenuTaxonomyService",
        "MenuThemeEnforcementAspect",
        "MenuThemePackSeedService",
        "MenuThemePackService",
        "MenuThemeService",
        "NutritionFactsService",
        "ServesPeopleSupport",
        "TaxonomySeedService",
    ],
    "order": [
        "BillAdjustmentService",
        "CartPricingService",
        "KitchenCloseGuard",
        "KitchenUberEatsMapper",
        "KitchenUberEatsService",
        "KitchenYemekSepetiService",
        "MenuOrderService",
        "MenuWaiterOrderService",
        "OrderAuditService",
        "TableBillService",
        "TableSessionService",
        "WaiterCourierOrderService",
    ],
    "analytics": [
        "AnalyticsService",
        "BatchReportService",
        "MenuFixedExpenseService",
        "SiteAnalyticsService",
        "SmartReportCompletionNotifier",
        "SmartReportModelInputBuilder",
        "SmartReportService",
        "UnifiedAnalyticsAggregates",
        "UnifiedAnalyticsService",
        "WaiterPerformanceReportService",
    ],
    "waiter": [
        "MenuWaiterBillService",
        "MenuWaiterCommissionQueryService",
        "MenuWaiterService",
        "WaiterAccessService",
        "WaiterCommissionService",
        "WorkShiftService",
    ],
    "branch": [
        "BranchScope",
        "BranchService",
        "RestaurantAreaService",
        "RestaurantLayoutService",
        "RestaurantTableService",
    ],
    "campaign": [
        "CampaignConfigSupport",
        "CampaignEvaluationService",
        "CampaignManualGrantService",
        "CampaignRewardService",
        "CampaignService",
        "CartStampCardProgressService",
        "CustomerCampaignRewardService",
    ],
    "billing": [
        "AddonPurchaseService",
        "BillingAddressService",
        "ExternalPackageResponseMapper",
        "ExternalPackageViewService",
        "PackagePricingService",
        "PaymentRequestMapper",
        "PublicPackageCatalogService",
        "PurchaseExpiryService",
        "PurchaseFulfillmentService",
        "PurchaseLogService",
        "PurchaseSelectionPolicy",
        "PurchaseService",
        "SubscriptionRefundPolicy",
        "UserAccountingService",
        "UserDebtService",
        "UserPackageAssignmentService",
    ],
    "qr": ["QrGenerationService", "QrService"],
    "stock": [
        "StockConsumptionService",
        "StockIngredientService",
        "StockQuantities",
        "StockRecipeService",
    ],
    "integration": [
        "IntegrationApprovalService",
        "IntegrationExportService",
        "IntegrationPublishService",
    ],
    "admin": ["AdminUserCredentialService", "AdminUserService"],
    "platform": [
        "IpGeoLookupService",
        "NotificationPublisherService",
        "PlatformFeedbackService",
    ],
    "merchant": ["MerchantCustomerService"],
    "product": [
        "MenuProductOptionService",
        "MenuProductPairingService",
        "MenuProductRatingService",
        "MenuProductSeedService",
        "ProductImageStorageService",
    ],
}

SERVICE_SUBDIRS: dict[str, tuple[str, str]] = {
    "service/campaign": ("campaign", "service"),
    "service/entitlement": ("billing", "service/entitlement"),
    "service/menuindex": ("product", "service/menuindex"),
}

DTOS: dict[str, list[str]] = {
    "account": [
        "AccessSessionResponse",
        "AccountDtos",
        "AccountOverviewDtos",
        "CustomerAuthDtos",
        "EmailVerificationDtos",
        "SessionContextResponse",
        "SessionEntitlementResponse",
        "SessionUserResponse",
        "UserAccessProfile",
    ],
    "menu": [
        "AiMenuImportDtos",
        "MenuDtos",
        "MenuWaiterDtos",
        "TaxonomyDtos",
        "ThemeDtos",
        "CatalogSeedDtos",
    ],
    "order": ["MenuOrderDtos", "TableBillDtos"],
    "analytics": [
        "AnalyticsDtos",
        "BatchReportDtos",
        "MenuFixedExpenseDtos",
        "SiteAnalyticsDtos",
        "SmartReportDtos",
        "SmartReportModelDtos",
    ],
    "branch": ["BranchDtos", "RestaurantAreaDtos", "RestaurantLayoutDtos", "RestaurantTableDtos"],
    "campaign": ["CampaignDtos"],
    "billing": [
        "AddonCartLineRequest",
        "AddonCartPurchaseRequest",
        "AddonPurchaseRequest",
        "BillingAddressDtos",
        "BillingAddressPageResponse",
        "ConsumedEntitlement",
        "CouponDtos",
        "InstallmentOptionResponse",
        "PaymentCardDto",
        "PaymentCompletedEventDto",
        "PaymentEventMetadata",
        "PlanChangeOptionResponse",
        "PlanChangePackageSummary",
        "PlanChangePreviewResponse",
        "PlanChangeRequest",
        "PlanChangeResponse",
        "PlanPackageItemRequest",
        "PlanPackageItemResponse",
        "PlanPackageModuleResponse",
        "PlanPackageRequest",
        "PlanPackageResponse",
        "PublishPackageRequest",
        "PurchaseFulfillmentResponse",
        "PurchaseInitiateResponse",
        "PurchaseLogResponse",
        "PurchaseModuleLineRequest",
        "PurchaseRequest",
        "PurchaseResponse",
        "PurchaseSummaryResponse",
        "SellablePackageComposeRequest",
        "SubscriptionOverviewResponse",
        "UserAccountingDtos",
        "UserEntitlementResponse",
        "AdminSubscriptionDtos",
    ],
    "qr": [
        "QrActiveRequest",
        "QrActiveResponse",
        "QrListPageResponse",
        "QrListResponse",
        "QrNameRequest",
        "QrNameResponse",
        "QrRequest",
        "QrResponse",
        "LinkRequest",
        "LocationRequest",
        "TextRequest",
        "WifiRequest",
        "MailRequest",
        "ContactRequest",
        "AddressDto",
    ],
    "product": [
        "ProductRequest",
        "ProductResponse",
        "MenuProductSeedDtos",
        "ProductImageDtos",
    ],
    "stock": ["StockDtos"],
    "integration": ["IntegrationPendingProductDtos", "FulfillmentDetailResponse"],
    "admin": ["AdminUserDtos"],
    "platform": ["PlatformFeedbackDtos"],
    "waiter": ["WorkShiftDtos"],
}


def class_to_module(class_map: dict[str, list[str]]) -> dict[str, str]:
    out: dict[str, str] = {}
    for module, names in class_map.items():
        for name in names:
            out[name] = module
    return out


def move_java(src: Path, dest: Path, new_package: str) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    text = src.read_text(encoding="utf-8")
    text = re.sub(
        r"^package\s+[\w.]+;",
        f"package {new_package};",
        text,
        count=1,
        flags=re.MULTILINE,
    )
    dest.write_text(text, encoding="utf-8")
    if src.exists():
        src.unlink()


def main() -> None:
    import_map: dict[str, str] = {}

    ctrl_mod = class_to_module(CONTROLLERS)
    for class_name, module in ctrl_mod.items():
        old_pkg = (
            "com.ael.algoryqrservice.controller.admin"
            if class_name.startswith("Admin")
            else "com.ael.algoryqrservice.controller"
        )
        new_pkg = f"com.ael.algoryqrservice.{module}.controller"
        import_map[f"{old_pkg}.{class_name}"] = f"{new_pkg}.{class_name}"

        if class_name.startswith("Admin"):
            src = JAVA_ROOT / "controller" / "admin" / f"{class_name}.java"
        else:
            src = JAVA_ROOT / "controller" / f"{class_name}.java"
        dest = JAVA_ROOT / module / "controller" / f"{class_name}.java"
        if src.exists():
            move_java(src, dest, new_pkg)

    svc_mod = class_to_module(SERVICES)
    for class_name, module in svc_mod.items():
        old_pkg = "com.ael.algoryqrservice.service"
        new_pkg = f"com.ael.algoryqrservice.{module}.service"
        import_map[f"{old_pkg}.{class_name}"] = f"{new_pkg}.{class_name}"
        src = JAVA_ROOT / "service" / f"{class_name}.java"
        dest = JAVA_ROOT / module / "service" / f"{class_name}.java"
        if src.exists():
            move_java(src, dest, new_pkg)

    for rel_sub, (module, sub) in SERVICE_SUBDIRS.items():
        sub_path = JAVA_ROOT / rel_sub
        if not sub_path.is_dir():
            continue
        for src in sub_path.glob("*.java"):
            class_name = src.stem
            old_pkg = f"com.ael.algoryqrservice.{rel_sub.replace('/', '.')}"
            new_pkg = f"com.ael.algoryqrservice.{module}.{sub.replace('/', '.')}"
            import_map[f"{old_pkg}.{class_name}"] = f"{new_pkg}.{class_name}"
            dest = JAVA_ROOT / module / sub / f"{class_name}.java"
            move_java(src, dest, new_pkg)

    dto_mod = class_to_module(DTOS)
    for class_name, module in dto_mod.items():
        old_pkg = "com.ael.algoryqrservice.model.dto"
        new_pkg = f"com.ael.algoryqrservice.{module}.dto"
        import_map[f"{old_pkg}.{class_name}"] = f"{new_pkg}.{class_name}"
        src = JAVA_ROOT / "model" / "dto" / f"{class_name}.java"
        dest = JAVA_ROOT / module / "dto" / f"{class_name}.java"
        if src.exists():
            move_java(src, dest, new_pkg)

    # Replace imports (longest keys first)
    java_files = list((JAVA_ROOT).rglob("*.java"))
    for old, new in sorted(import_map.items(), key=lambda x: len(x[0]), reverse=True):
        for path in java_files:
            text = path.read_text(encoding="utf-8")
            updated = text.replace(old, new)
            if updated != text:
                path.write_text(updated, encoding="utf-8")

    # Clean empty legacy dirs
    for rel in ["controller/admin", "controller", "service/campaign", "service/entitlement", "service/menuindex", "service", "model/dto"]:
        p = JAVA_ROOT / rel
        if p.is_dir() and not any(p.rglob("*.java")):
            shutil.rmtree(p, ignore_errors=True)

    print(f"Moved {len(import_map)} types into domain packages.")


if __name__ == "__main__":
    main()
