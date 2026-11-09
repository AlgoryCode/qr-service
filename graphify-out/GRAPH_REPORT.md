# Graph Report - qr-service  (2026-11-09)

## Corpus Check
- Large corpus: 1696 files · ~432,997 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder.

## Summary
- 13278 nodes · 51559 edges · 380 communities (238 shown, 142 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 3969 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- model/dto
- model/enums
- presentation/controller
- application/service
- presentation/controller
- qrservice/model
- model/enums
- shared/ui
- shared/model
- qrservice/exception
- qrservice/service
- qrservice/service
- qrservice/repository
- branch/service
- feature/integrations
- feature/reports
- qrservice/repository
- shared/session
- billservice/application
- analytics/service
- menu/service
- model/dto
- persistence/adapter
- menu/controller
- authservice/controller
- feature/waiter
- feature/waiter
- billing/service
- business/data
- qrservice/coupon
- scripts
- service/menuindex
- infrastructure/messaging
- application/service
- analytics/service
- order/service
- analytics/service
- qrservice/provider
- feature/products
- scripts
- paytr/mapper
- orderflow/service
- qrservice/model
- analytics/controller
- menu/service
- store/model
- scripts
- analytics/dto
- store/model
- product/service
- ubereats/mapper
- staffservice/application
- menu/service
- feature/flow
- billservice/application
- qrservice/service
- service/ubereats
- qrservice/service
- billing/service
- menu/service
- qrservice/security
- service/yemeksepeti
- branch/controller
- stock/service
- payment/handler
- product/service
- qr/service
- menu/service
- authservice/service
- presentation/controller
- service/entitlement
- qrservice/util
- print/service
- qrservice/client
- menu/service
- account/controller
- qrservice/config
- billing/service
- order/controller
- scripts
- model/enums
- print/service
- qrservice/config
- scripts
- application/auth
- qrservice/config
- scripts
- menu/dto
- store/service
- feature/waiter
- messaging/handler
- billing/service
- test/resources
- menu/controller
- model/dto
- service/odeal
- service/ubereats
- yemeksepeti/crypto
- print/service
- qrservice/config
- model/enums
- service/ubereatsmenu
- model/enums
- model/dto
- billing/service
- qrservice/repository
- store/service
- qrservice/security
- scripts
- yemeksepeti/mapper
- account/service
- scripts
- qrservice/client
- branch/service
- campaign/service
- analytics/service
- presentation/controller
- account/service
- qrservice/service
- analytics/service
- order/service
- model/dto
- staffservice/application
- iosStaffApp/iosApp
- analytics/service
- campaign/service
- menu/service
- stock/service
- analytics/service
- mobile/iosBusinessApp
- mobile/iosStaffApp
- ubereats/client
- model/dto
- qrservice/service
- application/auth
- analytics/dto
- store/service
- resources
- application/auth
- menu/service
- scripts
- branch/service
- model/enums
- store/service
- authservice/security
- menu/service
- scripts
- application/auth
- resources
- adminservice/application
- application/auth
- qrservice/access
- billing/service
- model/enums
- model/dto
- account/service
- qrservice/repository
- qrservice/security
- .github/workflows
- ubereatsmenu/config
- scripts
- infrastructure/client
- application/service
- billing/controller
- integration/service
- ubereatsmenu/client
- adminservice/application
- application/auth
- campaign/service
- analytics/service
- branch/service
- product/service
- resources
- qrservice/config
- misc
- scripts
- resources
- scripts
- model/dto
- model/enums
- resources
- qrservice/messaging
- qr/repository
- application/auth
- campaign/service
- order/service
- qrservice/security
- application/service
- qrservice/repository
- qrservice/config
- resources
- shared/ui
- adminservice/dto
- application/auth
- qrservice/repository
- order/dto
- scripts
- application/auth
- externalpackageresponsemapper/service
- model/dto
- client/dto
- model/dto
- domain/model
- stock/service
- scripts
- persistence/entity
- integration/service
- store/service
- qrservice/client
- platform/service
- menu/service
- model/dto
- domain/model
- docs/algoryqr-web-site
- scripts
- internal/controller
- persistence/adapter
- billing/service
- yemeksepeti/client
- qrservice/messaging
- qrservice/repository
- model/enums
- print/model
- resources
- resources
- application/auth
- presentation/controller
- scripts
- scripts
- campaign/service
- persistence/adapter
- analytics/dto
- analytics/service
- qrservice/repository
- qrservice/model
- model/dto
- platform/service
- store/service
- resources
- resources
- domain/exception
- scripts
- customerservice/application
- domain/model
- staffservice/application
- misc
- domain/model
- analytics/dto
- resources
- shared/session
- scripts
- scripts
- qrservice/model
- product/model
- resources
- algoryqr/staff
- application/auth
- internal/controller
- model/dto
- model/dto
- store/controller
- resources
- resources
- resources
- resources
- resources
- resources
- resources
- resources
- scripts
- scripts
- qrservice/security
- authservice/util
- qrservice/service
- ubereatsmenu/controller
- model/dto
- qrservice/util
- staffservice/application
- resources
- resources
- qrservice/config
- authservice/controller
- authservice/security
- model/dto
- model/dto
- resources
- resources
- resources
- resources
- algoryqr/business
- feature/reports
- resources
- resources
- feature/dashboard
- merchantservice/application
- yemeksepeti/client
- model/dto
- model/enums
- persistence/repository
- docs/algoryqr-web-site
- qrservice/config
- messaging/payment
- billing/dto
- client/dto
- model/dto
- model/dto
- model/dto
- model/dto
- model/dto
- qr/dto
- qr/dto
- qr/dto
- qr/dto
- misc

## God Nodes (most connected - your core abstractions)
1. `BadRequestException` - 361 edges
2. `SecurityUtils` - 169 edges
3. `Purchase` - 165 edges
4. `MenuService` - 160 edges
5. `Menu` - 152 edges
6. `NotFoundException` - 147 edges
7. `MenuRepository` - 145 edges
8. `MerchantStaff` - 129 edges
9. `PurchaseService` - 122 edges
10. `User` - 98 edges

## Surprising Connections (you probably didn't know these)
- `MainViewController()` --calls--> `App()`  [INFERRED]
  mobile/businessApp/src/iosMain/kotlin/com/algoryqr/business/MainViewController.kt → mobile/businessApp/src/commonMain/kotlin/com/algoryqr/business/App.kt
- `AppScreen()` --calls--> `actions()`  [INFERRED]
  mobile/shared/src/commonMain/kotlin/com/algoryqr/shared/ui/Components.kt → mobile/businessApp/src/commonMain/kotlin/com/algoryqr/business/feature/integrations/IntegrationsScreens.kt
- `AuthScaffold()` --calls--> `AppCard()`  [INFERRED]
  mobile/shared/src/commonMain/kotlin/com/algoryqr/shared/ui/StateViews.kt → mobile/shared/src/commonMain/kotlin/com/algoryqr/shared/ui/Components.kt
- `LoadContent()` --calls--> `LoadingContent()`  [INFERRED]
  mobile/shared/src/commonMain/kotlin/com/algoryqr/shared/ui/StateViews.kt → mobile/shared/src/commonMain/kotlin/com/algoryqr/shared/ui/Components.kt
- `LoadContent()` --calls--> `ErrorState()`  [INFERRED]
  mobile/shared/src/commonMain/kotlin/com/algoryqr/shared/ui/StateViews.kt → mobile/shared/src/commonMain/kotlin/com/algoryqr/shared/ui/Components.kt

## Import Cycles
- None detected.

## Communities (380 total, 142 thin omitted)

### Community 0 - "model/dto"
Cohesion: 0.01
Nodes (477): AddTipRequest, AdjustmentResponse, BillItemResponse, BillPaymentResponse, CancelBillRequest, ChangePaymentMethodRequest, CloseBillRequest, CreateAdjustmentRequest (+469 more)

### Community 1 - "model/enums"
Cohesion: 0.03
Nodes (34): BranchScope, CartStampCardProgressService, CatalogThemes, NotFoundException, UberEatsDtos, UberEatsOrderSpecifications, YemekSepetiConnectionStatus, CONNECTED (+26 more)

### Community 2 - "presentation/controller"
Cohesion: 0.02
Nodes (20): RefreshRequest, StaffBillController, PaymentController, CreatePaymentMethodRequest, CardStorageSessionResponse, CampaignPanelController, IntegrationApprovalController, OdealTestController (+12 more)

### Community 3 - "application/service"
Cohesion: 0.03
Nodes (59): CardStorageSessionDto, CheckoutFormResultDto, PaymentResultDto, RefundResultDto, PaymentApplicationMapper, CardStorageService, PaymentReconciliationScheduler, PaymentService (+51 more)

### Community 4 - "presentation/controller"
Cohesion: 0.04
Nodes (46): AdminPlatformFeedbackController, AdminPurchaseController, AdminSiteAnalyticsController, AdminUserController, StaffTableBillController, InternalMerchantController, MerchantMenuCustomerController, PaymentMethodController (+38 more)

### Community 5 - "qrservice/model"
Cohesion: 0.04
Nodes (81): DashboardUserSession, AppProperties, CorsSettings, JwtProperties, AdminEntity, DemoAccountEntity, GoogleAuthHandoffEntity, Pk (+73 more)

### Community 6 - "model/enums"
Cohesion: 0.02
Nodes (79): AddonCartPurchaseRequest, BillingAddressDtos, Request, Response, InstallmentOptionResponse, PlanChangeRequest, PurchaseFulfillmentResponse, PurchaseRequest (+71 more)

### Community 7 - "shared/ui"
Cohesion: 0.06
Nodes (34): AccountScreen(), DashboardScreen(), Group(), MoreItem, MoreSheet(), MoreTile(), ReservationsScreen(), StaffFormScreen() (+26 more)

### Community 8 - "shared/model"
Cohesion: 0.03
Nodes (86): TablesViewModel, AddTipRequest, Bill, BillItem, BillPayment, CancelOrderRequest, CancelReason, CUSTOMER_REQUEST (+78 more)

### Community 9 - "qrservice/exception"
Cohesion: 0.03
Nodes (29): AdminApiExceptionHandler, AuthGlobalExceptionHandler, NotFoundException, RequestUtils, BillApiExceptionHandler, CustomerApiExceptionHandler, DomainException, PackageAlreadyAssignedException (+21 more)

### Community 10 - "qrservice/service"
Cohesion: 0.02
Nodes (30): AdminUserCredentialService, PaymentServiceErrorMapper, TypeReference<>, PurchasesExpiredEvent, GeoLocation, IpApiResponse, IpGeoLookupService, JsonMapper (+22 more)

### Community 11 - "qrservice/service"
Cohesion: 0.06
Nodes (17): MenuProductRepository, MenuRepository, UserRepository, SecurityUtils, AiMenuImportServiceTest, IntegrationExportServiceTest, IntegrationPublishServiceTest, MenuFeedbackServiceTest (+9 more)

### Community 12 - "qrservice/repository"
Cohesion: 0.04
Nodes (18): AdminJpaRepository, GoogleAuthHandoffRepository, CustomerMembershipRepository, PlanPackageJpaRepository, UserEntitlementJpaRepository, PaymentEventOutboxJpaRepository, PrintPairingCodeRepository, IntegrationPendingProductRepository (+10 more)

### Community 13 - "branch/service"
Cohesion: 0.04
Nodes (32): BranchService, MenuPublicIdGenerator, MenuQrSoftDeleteService, Branch, LinkRequest, QrRequest, WifiRequest, MenuChannel (+24 more)

### Community 14 - "feature/integrations"
Cohesion: 0.04
Nodes (103): ConnectForm, ConnectionBranchRequest, ConnectionStatus, CONNECTED, DISCONNECTED, ERROR, PENDING_RESTAURANT, IntegrationRepository (+95 more)

### Community 15 - "feature/reports"
Cohesion: 0.04
Nodes (89): AreaChart(), ColumnChart(), DonutChart(), MiniBars(), OptionPills(), RangePills(), rememberReveal(), ShareBar() (+81 more)

### Community 16 - "qrservice/repository"
Cohesion: 0.02
Nodes (47): BillAdjustmentService, BillAdjustment, BillAdjustmentType, DISCOUNT, REFUND, VOID, TableBillStatus, CANCELLED (+39 more)

### Community 17 - "shared/session"
Cohesion: 0.03
Nodes (43): MainActivity, LoginScreen(), LoginState, LoginViewModel, AndroidSessionStorage, AccessResponse, AuthRepository, DemoLoginRequest (+35 more)

### Community 18 - "billservice/application"
Cohesion: 0.04
Nodes (9): TableBillService, TableBillPaymentMethod, CARD, CASH, TableBill, TableBillItem, TableSession, TableSessionRepository (+1 more)

### Community 19 - "analytics/service"
Cohesion: 0.04
Nodes (12): RevenuePaymentBreakdown, AnalyticsService, ChannelBundle, PersonnelPaymentAgg, PlatformChannelStats, PlatformSale, ReportScope, MenuProductVisitRepository (+4 more)

### Community 20 - "menu/service"
Cohesion: 0.05
Nodes (4): MenuService, Range, ServesPeopleSupport, MenuServiceTest

### Community 21 - "model/dto"
Cohesion: 0.04
Nodes (22): AddressRequest, BasketItemRequest, BootstrapSubscriptionRequest, BuyerRequest, CardVerificationInitRequest, CreatePaymentRequest, InitializeCheckoutFormRequest, PaymentCardRequest (+14 more)

### Community 22 - "persistence/adapter"
Cohesion: 0.04
Nodes (22): PackageCatalogService, PackageCatalogServiceImpl, FulfillmentStatus, ACTIVE, REVOKED, SUPERSEDED, PackageFulfillment, PackageKind (+14 more)

### Community 24 - "authservice/controller"
Cohesion: 0.05
Nodes (34): AdminUserImpersonationService, AuthSubject, DemoUsernameAvailabilityUseCase, GoogleIdentityUseCase, GoogleLoginUseCase, GoogleProviders, GoogleSubjectResolver, GoogleSubjectStrategy (+26 more)

### Community 25 - "feature/waiter"
Cohesion: 0.03
Nodes (56): OrderStatus, CANCELLED, CONFIRMED, DRAFT, PAID, PREPARING, READY, REJECTED (+48 more)

### Community 26 - "feature/waiter"
Cohesion: 0.04
Nodes (42): ProductRow(), ProductsScreen(), formatMoney(), searchKey(), CatalogProduct, AppTextField(), ChoiceChip(), Cart (+34 more)

### Community 27 - "billing/service"
Cohesion: 0.05
Nodes (10): PurchaseResponse, LifecycleSnapshot, PurchaseService, RefundPrep, SubscriptionRefundPolicy, BadRequestException, ForbiddenException, Purchase (+2 more)

### Community 28 - "business/data"
Cohesion: 0.05
Nodes (69): AssignBranchRequest, Branch, BranchListResponse, BranchMenu, BrowseFunnel, ChannelDailyPoint, ChannelShare, CreateProductRequest (+61 more)

### Community 29 - "qrservice/coupon"
Cohesion: 0.05
Nodes (31): AmountCouponDiscount, CouponDiscount, CouponDiscountCalculator, CouponLogWriter, CouponUseCases, CouponPolicy, CouponQuote, PercentCouponDiscount (+23 more)

### Community 30 - "scripts"
Cohesion: 0.03
Nodes (34): column_exists(), main(), run_sql_file(), table_exists(), apply(), flyway_has(), record_flyway(), table_exists() (+26 more)

### Community 31 - "service/menuindex"
Cohesion: 0.05
Nodes (23): AiServiceClient, MenuProductReindexDtos, Request, Response, AiServiceProperties, MenuEventsRabbitProperties, BillClosedAccountingListener, MenuProductDocumentMessage (+15 more)

### Community 32 - "infrastructure/messaging"
Cohesion: 0.04
Nodes (17): PaymentModuleQrEventBridge, PaymentCompletedEvent, PaymentEventPublisherPort, PaymentEventsRabbitMqProperties, OutboxPaymentEventPublisherAdapter, TransactionSynchronization, RabbitMqPaymentEventPublisherAdapter, RabbitMqPaymentNotificationPublisherAdapter (+9 more)

### Community 33 - "application/service"
Cohesion: 0.05
Nodes (15): PaymentMethodService, StoredPaymentMethod, SubscriptionRenewalScheduler, SubscriptionService, TokenizeCardCommand, TokenizedCard, TypeReference<>, SubscriptionEntity (+7 more)

### Community 34 - "analytics/service"
Cohesion: 0.05
Nodes (11): DailyOrderPoint, HourlyOrderPoint, KitchenAnalytics, KitchenHourlyLoad, KitchenStageKpis, OrdersAnalytics, StatusCount, TableAcc (+3 more)

### Community 35 - "order/service"
Cohesion: 0.06
Nodes (3): KitchenRoutingService, MenuOrderService, MenuWaiterOrderService

### Community 36 - "analytics/service"
Cohesion: 0.04
Nodes (14): BillPayment, ProductAgg, ReportContext, WaiterPerformanceReportService, WaiterStats, WaiterCommissionRecordType, FIXED_ITEM_ADD, FIXED_TABLE_CLOSE (+6 more)

### Community 37 - "qrservice/provider"
Cohesion: 0.06
Nodes (22): QrProviderFactory, Type, CONTACT, LINK, LOCATION, MAIL, MENU, TEXT (+14 more)

### Community 38 - "feature/products"
Cohesion: 0.04
Nodes (45): App(), BranchesViewModel, BranchPicker(), MainShell(), NoMenuScreen(), ShellContent(), BusinessContainer, MoreDestination (+37 more)

### Community 39 - "scripts"
Cohesion: 0.04
Nodes (38): main(), should_skip(), apply_import_map(), cleanup_empty_dirs(), flatten_layer_paths(), fqcn_from_path(), main(), move_type() (+30 more)

### Community 40 - "paytr/mapper"
Cohesion: 0.05
Nodes (9): CheckoutFormKind, IFRAME, CheckoutFormInitializerPort, PaytrPaymentGatewayAdapter, CheckoutFormInitializerFactory, PaytrIframeCheckoutInitializer, PaytrApiClient, PaytrRequestMapper (+1 more)

### Community 41 - "orderflow/service"
Cohesion: 0.05
Nodes (35): OrderFlowChannel, DELIVERY, TABLE, OrderFlowRequestStatus, DONE, IN_REVIEW, OPEN, REJECTED (+27 more)

### Community 42 - "qrservice/model"
Cohesion: 0.07
Nodes (19): PlanChangeResponse, PlanChangeResponse, CampaignProgressStatus, COMPLETED, IN_PROGRESS, REWARDED, PlanChangeDirection, DOWNGRADE (+11 more)

### Community 43 - "analytics/controller"
Cohesion: 0.04
Nodes (3): MenuCustomerListService, AnalyticsController, MenuFixedExpenseController

### Community 44 - "menu/service"
Cohesion: 0.05
Nodes (6): MenuFeedbackService, MenuRatingService, MenuProductRatingRepository, MenuProductRatingService, MenuRatingRepository, MenuRatingServiceTest

### Community 45 - "store/model"
Cohesion: 0.04
Nodes (39): Address, CourierBrief, CourierStaffBrief, OrderDetail, OrderPage, OrderSummary, StatusEvent, StoreOrderDtos (+31 more)

### Community 46 - "scripts"
Cohesion: 0.06
Nodes (46): api_json(), download_image(), load_excel_rows(), load_products(), login_token(), main(), match_product(), normalize() (+38 more)

### Community 47 - "analytics/dto"
Cohesion: 0.07
Nodes (67): AnalyticsCoverage, AnalyticsDtos, AnalyticsEventItemRequest, AnalyticsEventsRequest, AnalyticsPeriod, CancellationByWaiter, CancellationsAnalytics, ChannelDailyPoint (+59 more)

### Community 48 - "store/model"
Cohesion: 0.05
Nodes (26): WaiterCourierOrderService, CreateOrderRequest, DeliveryAddressRequest, StoreOrder, StoreOrderStatus, CANCELLED, CONFIRMED, COURIER_DELIVERED_TO_CUSTOMER (+18 more)

### Community 49 - "product/service"
Cohesion: 0.06
Nodes (20): MenuProductOptionGroupKind, CHOICE, CUSTOM, EXTRA, PORTION, REMOVAL, SIZE, MenuProductOptionUnit (+12 more)

### Community 51 - "staffservice/application"
Cohesion: 0.06
Nodes (5): MemberPackageAccessRepairWorker, AccessDecision, MenuPublicAccessService, TaxonomySeedService, MenuWaiterService

### Community 52 - "menu/service"
Cohesion: 0.07
Nodes (3): MenuCategoryService, MenuCategory, MenuCategoryRepository

### Community 53 - "feature/flow"
Cohesion: 0.07
Nodes (36): CreateFlowRequest, FlowBranchRef, FlowCatalog, FlowRequest, FlowStatusInfo, FlowStepInfo, FlowTemplateInfo, OrderFlow (+28 more)

### Community 54 - "billservice/application"
Cohesion: 0.07
Nodes (5): StaffBillService, BillResponse, StaffAccessService, MerchantStaff, MerchantStaffRepository

### Community 55 - "qrservice/service"
Cohesion: 0.08
Nodes (6): BillingAddressController, BillingAddressService, BillingAddress, BillingAddressRepository, BillingAddressControllerTest, BillingAddressServiceTest

### Community 56 - "service/ubereats"
Cohesion: 0.07
Nodes (12): IntegrationPublishService, UberEatsConnectionService, UberEatsCredentialEncryptor, UberEatsConnection, UberEatsConnectionStatus, CONNECTED, DISCONNECTED, ERROR (+4 more)

### Community 57 - "qrservice/service"
Cohesion: 0.09
Nodes (7): PaymentCardDto, PaymentRequestMapper, BasketItemPayload, AppProperties, PaymentClientProperties, User, PaymentRequestMapperTest

### Community 58 - "billing/service"
Cohesion: 0.08
Nodes (6): PaymentCompletedEventDto, PaymentEventMetadata, RefundablePayment, InvalidPaymentEventException, PaymentCompletedEventDto, PaymentEventMetadata

### Community 59 - "menu/service"
Cohesion: 0.07
Nodes (7): MenuThemeEnforcementAspect, MenuThemePackService, MenuThemeService, MenuTheme, UserThemeAssignment, MenuThemeRepository, UserThemeAssignmentRepository

### Community 60 - "qrservice/security"
Cohesion: 0.08
Nodes (10): AuthJwtAuthenticationFilter, AuthSecurityConfig, GoogleOAuthLoopbackHostNormalizerFilter, AccessSessionMapper, AccessSessionResponse, AuthErrorCodes, PrintDeviceAuthenticationFilter, InternalApiTokenFilter (+2 more)

### Community 61 - "service/yemeksepeti"
Cohesion: 0.07
Nodes (8): YemekSepetiConnectionService, InstantRange, OrderAction, YemekSepetiOrderService, TypeReference<>, YemekSepetiReconcileScheduler, YemekSepetiConnection, YemekSepetiConnectionRepository

### Community 62 - "branch/controller"
Cohesion: 0.06
Nodes (5): BranchController, OrderFlowController, ProductScopeAspect, RequiresProductScope, StockController

### Community 63 - "stock/service"
Cohesion: 0.10
Nodes (19): StockMovementKind, ADJUST, IN, OUT, SALE, SALE_REVERSAL, StockSourceType, MANUAL (+11 more)

### Community 64 - "payment/handler"
Cohesion: 0.06
Nodes (14): DemoPackageAssignment, FulfillmentPaymentBridge, FulfillmentService, CardVerificationRefundScheduler, PurchasesExpiredListener, UberEatsReconcileScheduler, PaymentEventHandler, PaymentEventHandlerRegistry (+6 more)

### Community 66 - "qr/service"
Cohesion: 0.09
Nodes (5): QrPublicResponse, QrPayloads, TypeReference<>, CatalogThemesTest, QrPayloadsTest

### Community 67 - "menu/service"
Cohesion: 0.08
Nodes (7): MenuTaxonomyService, MenuAllergen, MenuTag, MenuProductSeedService, MenuAllergenRepository, MenuTagRepository, MenuTaxonomyServiceTest

### Community 68 - "authservice/service"
Cohesion: 0.08
Nodes (18): GoogleAuthErrorCode, ACCESS_DENIED, ACCOUNT_EXISTS, ACCOUNT_NOT_FOUND, EMAIL_NOT_VERIFIED, GOOGLE_AUTH_FAILED, INVALID_CLIENT, INVALID_INTENT (+10 more)

### Community 69 - "presentation/controller"
Cohesion: 0.07
Nodes (13): ActivePackageView, FulfillmentController, AssignPackageRequest, EntitlementQuantityRequest, ActivePackageResponse, ConsumeEntitlementResponse, EntitlementResponse, FulfillmentResponse (+5 more)

### Community 70 - "service/entitlement"
Cohesion: 0.08
Nodes (8): SessionAccessPolicy, AddonCartLineRequest, AddonPurchaseRequest, PurchaseSelectionPolicy, PurchaseLifecycleTest, PurchaseTimezoneTest, AddonPurchaseServiceTest, PurchaseSelectionPolicyTest

### Community 71 - "qrservice/util"
Cohesion: 0.06
Nodes (6): Customer, CustomerRepository, AuthMerchantClient, MerchantView, JwtAccessPrincipal, SecurityUtilsStaffTest

### Community 73 - "print/service"
Cohesion: 0.08
Nodes (4): PrintAgentController, PrintAgentManageController, PrintAgentDeviceService, PrintAgentTokenService

### Community 74 - "qrservice/client"
Cohesion: 0.08
Nodes (15): BillingPaymentDtos, CardVerificationInit, InstallmentOption, InstallmentOptions, InstallmentProvider, PaymentDetail, PaymentMethod, PaymentPage (+7 more)

### Community 75 - "menu/service"
Cohesion: 0.09
Nodes (6): MenuCatalogCloneService, TaxonomyCloneResult, ProductNotInPackageException, ProductUsageGateway, KitchenCloseGuardTest, MenuCatalogCloneServiceTest

### Community 76 - "account/controller"
Cohesion: 0.06
Nodes (7): AccountController, EmailVerificationDtos, PublicVerifyRequest, ResendByEmailRequest, Status, VerifyRequest, AccountCredentialGateway

### Community 77 - "qrservice/config"
Cohesion: 0.07
Nodes (9): GoogleOAuthStartupValidator, StartupRepairRunner, CampaignTemplateSeedRunner, PurchaseSchemaCompatibilityFixer, ChefAvatarSeedRunner, TaxonomySeedRunner, CampaignTemplate, MenuProductSeedRunner (+1 more)

### Community 78 - "billing/service"
Cohesion: 0.08
Nodes (8): LinePrice, PackagePricingService, PriceBreakdown, CatalogPackages, PlanPackageItem, Product, ProductRepository, PackagePricingServiceTest

### Community 79 - "order/controller"
Cohesion: 0.06
Nodes (3): CustomerOrderController, PublicOrderController, PublicTableSessionController

### Community 80 - "scripts"
Cohesion: 0.11
Nodes (28): decrypt(), dump(), encrypt(), normalize_key(), probe_tgo(), main(), encrypt(), normalize_key() (+20 more)

### Community 81 - "model/enums"
Cohesion: 0.05
Nodes (32): PurchaseLogAction, COUPON_APPLIED, ENTITLEMENT_CONSUMED, ENTITLEMENT_GRANTED, PLAN_CHANGE_CANCELLED, PLAN_CHANGE_COMPLETED, PLAN_CHANGE_ENTITLEMENTS_RESET, PLAN_CHANGE_PAYMENT_FAILED (+24 more)

### Community 82 - "print/service"
Cohesion: 0.09
Nodes (10): PrintSourceType, MENU_ORDER, STORE_ORDER, UBER_EATS, YEMEKSEPETI, PrintAgentDeviceRepository, PrintJobRepository, PrintDeviceLookupService (+2 more)

### Community 83 - "qrservice/config"
Cohesion: 0.12
Nodes (5): PaymentModuleRabbitMqConfig, IntegrationRabbitProperties, PaymentRabbitMqProperties, RabbitMqConfig, SmartReportRabbitProperties

### Community 84 - "scripts"
Cohesion: 0.05
Nodes (32): doc, fs, lines, __dirname, fetchJson(), loadSource(), main(), outPath (+24 more)

### Community 85 - "application/auth"
Cohesion: 0.10
Nodes (11): AuthSubjectMapper, DemoAccountRegistration, DemoAccountSupport, DemoLoginUseCase, RegisterMerchantUseCase, AuthErrorCodes, RoleEntity, DemoAccountJpaRepository (+3 more)

### Community 86 - "qrservice/config"
Cohesion: 0.07
Nodes (14): AuthModuleConfiguration, RestClientConfig, SecureRandomConfig, FulfillmentModuleConfiguration, PaymentModuleConfiguration, PaymentCallbackProperties, PaymentReconciliationProperties, PaymentTokenProperties (+6 more)

### Community 87 - "scripts"
Cohesion: 0.10
Nodes (32): load_drinks(), main(), normalize(), classify_drink(), classify_food(), main(), normalize(), set_cover_from_file() (+24 more)

### Community 88 - "menu/dto"
Cohesion: 0.07
Nodes (39): AssignBranchRequest, CatalogProduct, CatalogResponse, CreateWaiterRequest, CustomerListItem, MenuWaiterDtos, OwnerSummary, StaffListResponse (+31 more)

### Community 89 - "store/service"
Cohesion: 0.08
Nodes (10): CourierRequest, CourierResponse, StoreCourier, StoreCourierVehicleType, BICYCLE, CAR, MOTORCYCLE, ON_FOOT (+2 more)

### Community 90 - "feature/waiter"
Cohesion: 0.11
Nodes (21): QuantityStepper(), AmountEntry(), BillLines(), ConfirmPaymentDialog(), formatPlain(), ItemPayRow(), PayBar(), PayGroup (+13 more)

### Community 91 - "messaging/handler"
Cohesion: 0.09
Nodes (10): FulfillmentCommandFactory, InvalidPaymentEventException, PaymentCompletedEvent, JacksonPaymentEventPayloadConverter, PaymentEventPayloadConverter, PaymentEventHandler, PaymentEventHandlerRegistry, PaymentIgnoredEventHandler (+2 more)

### Community 92 - "billing/service"
Cohesion: 0.09
Nodes (8): PlanPackageAssignmentPolicy, DebtView, UserDebtService, PackageControl, PackageRight, UserPackageAssignmentService, PlanPackage, PlanPackageRepository

### Community 93 - "test/resources"
Cohesion: 0.05
Nodes (39): app, url, registration, driver-class-name, password, url, username, enabled (+31 more)

### Community 94 - "menu/controller"
Cohesion: 0.09
Nodes (3): AdminTaxonomyController, TooManyRequestsException, MenuCategoryController

### Community 95 - "model/dto"
Cohesion: 0.06
Nodes (18): MyProfileResponse, AccountOverviewDtos, OverviewResponse, Section, BillingAddressPageResponse, SubscriptionOverviewResponse, FulfillmentDetailResponse, FulfillmentDetailResponse (+10 more)

### Community 96 - "service/odeal"
Cohesion: 0.11
Nodes (5): OdealClient, OdealProperties, ProxyResponse, OdealTestService, OdealTestServiceTest

### Community 97 - "service/ubereats"
Cohesion: 0.09
Nodes (5): InstantRange, OrderAction, UberEatsOrderService, TypeReference<>, UberEatsOrderServiceTest

### Community 98 - "yemeksepeti/crypto"
Cohesion: 0.10
Nodes (4): PaymentTokenCipher, YemekSepetiCredentialEncryptor, QrPublicCodes, StoreTokenGenerator

### Community 100 - "qrservice/config"
Cohesion: 0.08
Nodes (3): StorageHealthIndicator, ProductImageDtos, UploadResponse

### Community 101 - "model/enums"
Cohesion: 0.10
Nodes (14): EmailChangeService, EmailChangeChallengeStatus, AWAITING_CURRENT_CODE, AWAITING_NEW_CODE, CANCELLED, COMPLETED, CURRENT_VERIFIED, EmailChangeCodePurpose (+6 more)

### Community 102 - "service/ubereatsmenu"
Cohesion: 0.11
Nodes (9): UberEatsMenuConnectionService, UberEatsMenuCredentialEncryptor, HttpUberEatsMenuPublisher, UberEatsMenuConnection, UberEatsMenuConnectionStatus, CONNECTED, DISCONNECTED, ERROR (+1 more)

### Community 103 - "model/enums"
Cohesion: 0.09
Nodes (12): BusinessType, CAFE, HOTEL, OTHER, RESTAURANT, BusinessTypeConverter, UsagePurpose, DIGITAL_MENU (+4 more)

### Community 104 - "model/dto"
Cohesion: 0.08
Nodes (28): AdminUserDtos, ImpersonateResponse, PackageLifecycleResponse, PackageUpdateRequest, PasswordResetResponse, UserDetailResponse, UserPageResponse, UserSummaryResponse (+20 more)

### Community 105 - "billing/service"
Cohesion: 0.11
Nodes (10): PurchaseFulfillmentService, FulfillmentQueuePublisher, FulfillmentStatus, FAILED, OVERDUE, PAID, PENDING, REVOKED (+2 more)

### Community 106 - "qrservice/repository"
Cohesion: 0.09
Nodes (3): RestaurantTable, TableSessionService, RestaurantTableRepository

### Community 107 - "store/service"
Cohesion: 0.13
Nodes (14): BranchOption, BusinessInfo, DeliverySettings, MenuOption, MerchantResponse, SetupPrefillResponse, SetupRequest, StoreDtos (+6 more)

### Community 109 - "scripts"
Cohesion: 0.10
Nodes (21): assign_all_products(), ensure_schema(), fold_turkish(), load_descriptor_maps(), load_seed_descriptors(), load_sub_ids_by_slug(), main(), merge_descriptors() (+13 more)

### Community 112 - "scripts"
Cohesion: 0.11
Nodes (24): cleanup(), connect(), ensure_branch(), ensure_catalog(), ensure_menu(), ensure_staff(), ensure_tables(), execute_chunks() (+16 more)

### Community 113 - "qrservice/client"
Cohesion: 0.12
Nodes (4): ExternalConsumeResponse, ExternalEntitlementResponse, ExternalProductAccessResponse, FulfillmentServiceClient

### Community 117 - "analytics/service"
Cohesion: 0.10
Nodes (4): SmartReportCompletionNotifier, SmartReportService, BatchReportReconcileScheduler, SmartReportStatusMessage

### Community 118 - "presentation/controller"
Cohesion: 0.09
Nodes (6): DashboardSecurityUtils, AdminCouponController, AdminThemeController, DashboardAuthController, LogoutRequest, AdminCouponControllerTest

### Community 119 - "account/service"
Cohesion: 0.11
Nodes (3): AccountFacadeService, SupplierWithException, AccountService

### Community 120 - "qrservice/service"
Cohesion: 0.14
Nodes (5): PurchaseModuleLineRequest, CartPricing, CartPricingService, ModuleLine, CartPricingServiceTest

### Community 121 - "analytics/service"
Cohesion: 0.10
Nodes (10): DailyCount, NamedCount, RecordVisitRequest, SiteAnalyticsDtos, SummaryResponse, VisitItemResponse, VisitPageResponse, DateRange (+2 more)

### Community 122 - "order/service"
Cohesion: 0.12
Nodes (4): KitchenUberEatsMapper, KitchenUberEatsService, TypeReference<>, KitchenUberEatsMapperTest

### Community 123 - "model/dto"
Cohesion: 0.09
Nodes (30): IngredientPageResponse, IngredientRequest, IngredientResponse, IngredientUpdateRequest, MovementPageResponse, MovementRequest, MovementResponse, RecipeLineRequest (+22 more)

### Community 124 - "staffservice/application"
Cohesion: 0.10
Nodes (6): WorkShiftStatus, CLOSED, OPEN, WorkShift, WorkShiftRepository, WorkShiftService

### Community 125 - "iosStaffApp/iosApp"
Cohesion: 0.09
Nodes (14): BusinessApp, ComposeView, ContentView, .body, iOSApp, .body, ComposeView, ContentView (+6 more)

### Community 126 - "analytics/service"
Cohesion: 0.12
Nodes (3): MenuFixedExpenseService, MenuFixedExpense, MenuFixedExpenseRepository

### Community 127 - "campaign/service"
Cohesion: 0.10
Nodes (7): CampaignRewardService, CampaignRewardClaim, CampaignClaimStatus, CLAIMED, EXPIRED, PENDING, CampaignRewardClaimRepository

### Community 130 - "analytics/service"
Cohesion: 0.17
Nodes (5): BatchReportService, BatchSnapshot, ItemOutcome, ReconcileOutcome, BatchReportItemRepository

### Community 131 - "mobile/iosBusinessApp"
Cohesion: 0.07
Nodes (28): CODE_SIGN_STYLE, CURRENT_PROJECT_VERSION, ENABLE_USER_SCRIPT_SANDBOXING, FRAMEWORK_SEARCH_PATHS, GENERATE_INFOPLIST_FILE, INFOPLIST_FILE, MARKETING_VERSION, OTHER_LDFLAGS (+20 more)

### Community 132 - "mobile/iosStaffApp"
Cohesion: 0.07
Nodes (28): CODE_SIGN_STYLE, CURRENT_PROJECT_VERSION, ENABLE_USER_SCRIPT_SANDBOXING, FRAMEWORK_SEARCH_PATHS, GENERATE_INFOPLIST_FILE, INFOPLIST_FILE, MARKETING_VERSION, OTHER_LDFLAGS (+20 more)

### Community 134 - "model/dto"
Cohesion: 0.14
Nodes (28): AnalyticsDtos, DailyReportPoint, DailyVisitResponse, FeedbackCommentSample, FunnelCounts, HourlyReportPoint, JourneyStep, KitchenAnalytics (+20 more)

### Community 135 - "qrservice/service"
Cohesion: 0.16
Nodes (4): AdminUserService, UserAccessProfile, UserAccessProfileService, AdminUserServiceTest

### Community 136 - "application/auth"
Cohesion: 0.22
Nodes (6): AuthResponseAssembler, RefreshMatch, TokenUseCase, SessionEntity, SessionJpaRepository, TokenUseCaseTest

### Community 137 - "analytics/dto"
Cohesion: 0.11
Nodes (24): ChannelShare, DailyRevenuePoint, DailyVisitPoint, FunnelCounts, HourlyRevenuePoint, HourlyVisitPoint, Meta, OptionsBlock (+16 more)

### Community 138 - "store/service"
Cohesion: 0.12
Nodes (4): KitchenCourierOption, StoreOrderStatusHistory, StoreOrderStatusHistoryRepository, StoreOrderPanelService

### Community 139 - "resources"
Cohesion: 0.07
Nodes (28): card-verification-frontend-redirect-url, checkout-form-public-url, frontend-redirect-url, public-url, allowed-service-names, exchange, default-provider, fixed-delay (+20 more)

### Community 140 - "application/auth"
Cohesion: 0.15
Nodes (5): CodeResponse, PasswordChangeUseCase, TooManyRequestsException, PasswordChangeCodeEntity, PasswordChangeCodeJpaRepository

### Community 141 - "menu/service"
Cohesion: 0.13
Nodes (6): MenuThemePackSeedRunner, MenuThemePackSeedService, MenuThemePackSeedServiceTest, ByteArrayResource, ByteArrayResource, ByteArrayResource

### Community 142 - "scripts"
Cohesion: 0.11
Nodes (11): assign_ultimate(), db_config(), ensure(), load_catalog_package(), main(), read_active_package(), require_runtime_schema(), resolve_merchant_id() (+3 more)

### Community 143 - "branch/service"
Cohesion: 0.18
Nodes (3): RestaurantLayoutService, RestaurantLayout, RestaurantLayoutRepository

### Community 144 - "model/enums"
Cohesion: 0.09
Nodes (6): Campaign, CampaignStatus, ACTIVE, DRAFT, EXPIRED, PAUSED

### Community 145 - "store/service"
Cohesion: 0.11
Nodes (5): OrderTrackingResponse, StoreOrderItem, StorefrontResolver, StorefrontService, StoreOrderRateLimiter

### Community 147 - "menu/service"
Cohesion: 0.15
Nodes (5): NutritionFactsService, NutritionBasis, PER_100G, PER_100ML, NutritionFactsServiceTest

### Community 148 - "scripts"
Cohesion: 0.23
Nodes (22): assign_used_quantity(), clone_categories(), clone_options(), clone_products(), clone_subcategories(), clone_tags_allergens(), create_menu(), create_qr() (+14 more)

### Community 149 - "application/auth"
Cohesion: 0.10
Nodes (6): AdminProfileBuilder, CourierProfileBuilder, KitchenProfileBuilder, MerchantProfileBuilder, ProfileBuilder, StaffProfileBuilder

### Community 150 - "resources"
Cohesion: 0.08
Nodes (26): app, auth-gateway, cors, email-verification, fulfillment, internal-api-token, menu, qr (+18 more)

### Community 151 - "adminservice/application"
Cohesion: 0.16
Nodes (7): DashboardSessionService, RefreshTokenParts, DashboardUserSessionRepository, JwtPropertiesHelper, UnauthorizedException, AuthResponse, RefreshTokenRequest

### Community 152 - "application/auth"
Cohesion: 0.24
Nodes (4): EmailVerificationUseCase, Status, BadRequestException, MerchantEntity

### Community 153 - "qrservice/access"
Cohesion: 0.15
Nodes (10): AccessSession, SessionAccessService, AccessSessionResponse, SessionContextResponse, SessionEntitlementResponse, AccessDecision, ALLOW, REQUIRE_PAYMENT (+2 more)

### Community 154 - "billing/service"
Cohesion: 0.15
Nodes (5): PlanPackageModuleResponse, PlanPackageResponse, PublicPackageCatalogService, PlanPackageAddon, PlanPackageAddonRepository

### Community 155 - "model/enums"
Cohesion: 0.11
Nodes (21): DetailLineItem, EntryDetailResponse, EntryPageResponse, EntryResponse, SummaryTotals, UserAccountingDtos, DetailLineItem, EntryDetailResponse (+13 more)

### Community 156 - "model/dto"
Cohesion: 0.12
Nodes (24): ChannelShare, DailyRevenuePoint, DailyVisitPoint, FunnelCounts, HourlyRevenuePoint, HourlyVisitPoint, Meta, OptionsBlock (+16 more)

### Community 157 - "account/service"
Cohesion: 0.20
Nodes (7): SessionContextResponse, SessionEntitlementResponse, SessionUserResponse, Parts, SessionContextAssembler, SessionContextService, MerchantProfileView

### Community 158 - "qrservice/repository"
Cohesion: 0.13
Nodes (12): FeedbackItemResponse, FeedbackPageResponse, PlatformFeedbackDtos, PlatformFeedbackStatus, IN_PROGRESS, OPEN, RESOLVED, PlatformFeedback (+4 more)

### Community 159 - "qrservice/security"
Cohesion: 0.17
Nodes (3): EmailVerificationGate, EmailVerificationGatewayFilter, EmailVerificationGatewayFilterTest

### Community 160 - ".github/workflows"
Cohesion: 0.09
Nodes (22): permissions, runs-on, steps, jobs, build-and-deploy-staging, name, on, push (+14 more)

### Community 161 - "ubereatsmenu/config"
Cohesion: 0.15
Nodes (5): OdealClientConfig, UberEatsClientConfig, UberEatsMenuClientConfig, UberEatsMenuProperties, YemekSepetiClientConfig

### Community 162 - "scripts"
Cohesion: 0.23
Nodes (20): create_menu(), create_qr(), ensure_branch(), existing_menu(), fetch_all(), fetch_one(), generate_public_id(), generate_qr_base64() (+12 more)

### Community 163 - "infrastructure/client"
Cohesion: 0.15
Nodes (5): PackageState, MerchantLoginPackageLookup, ActivePackage, MemberPackageAccessRepairService, ScheduledRepairJob

### Community 164 - "application/service"
Cohesion: 0.18
Nodes (3): FulfillmentServiceImpl, ResourceNotFoundException, FulfillmentRepositoryPort

### Community 165 - "billing/controller"
Cohesion: 0.18
Nodes (4): PurchaseController, PurchaseInitiateResponse, CardSnapshot, PurchaseControllerCouponTest

### Community 166 - "integration/service"
Cohesion: 0.19
Nodes (3): IntegrationExportService, UberEatsProductCommandService, UberEatsProductCommandServiceTest

### Community 167 - "ubereatsmenu/client"
Cohesion: 0.18
Nodes (4): CachedToken, UberEatsMenuAuthClient, PublishResult, UberEatsMenuPublisher

### Community 168 - "adminservice/application"
Cohesion: 0.19
Nodes (6): DashboardAuthService, DashboardUser, DashboardUserRepository, LoginRequest, ClientInfo, DashboardAuthServiceTest

### Community 169 - "application/auth"
Cohesion: 0.22
Nodes (5): IssuedTokens, RefreshTokenParts, SessionIssuer, TokenAccess, SessionIssuerRotateTest

### Community 170 - "campaign/service"
Cohesion: 0.12
Nodes (5): CustomerMembership, CampaignManualGrantService, MembershipStatus, ACTIVE, LEFT

### Community 173 - "product/service"
Cohesion: 0.17
Nodes (3): MenuProductPairingRepository, MenuProductPairingService, MenuProductPairingServiceTest

### Community 174 - "resources"
Cohesion: 0.09
Nodes (21): ai-service, connect-timeout, read-timeout, reindex-batch-size, url, batch-reports, reconcile-interval-ms, google (+13 more)

### Community 176 - "misc"
Cohesion: 0.10
Nodes (20): command, depends_on, image, ports, volumes, command, image, volumes (+12 more)

### Community 177 - "scripts"
Cohesion: 0.24
Nodes (19): apply_assignments(), assign_matches(), canonical_size(), core_key(), fold(), is_generic(), load_aya_drinks(), load_excel_drinks() (+11 more)

### Community 178 - "resources"
Cohesion: 0.10
Nodes (21): main(), name, import, enabled, ignore-migration-patterns, ignore-missing-migrations, out-of-order, validate-on-migrate (+13 more)

### Community 179 - "scripts"
Cohesion: 0.22
Nodes (15): audit(), checksum_sql(), cmd_audit(), cmd_migrate(), cmd_rename(), cmd_snapshot(), cmd_validate(), flyway_applied() (+7 more)

### Community 180 - "model/dto"
Cohesion: 0.11
Nodes (6): AddonPurchaseRequest, BillingAddressDtos, Request, Response, PurchaseModuleLineRequest, PurchaseRequest

### Community 181 - "model/enums"
Cohesion: 0.13
Nodes (8): PlanChangeStatus, CANCELLED, COMPLETED, FAILED, PENDING_PAYMENT, SCHEDULED, PlanChange, PlanChangeRepository

### Community 182 - "resources"
Cohesion: 0.10
Nodes (21): integration, rabbitmq, menu, rabbitmq, rabbitmq, ai-completed-queue, ai-completed-routing-key, ai-requested-queue (+13 more)

### Community 183 - "qrservice/messaging"
Cohesion: 0.16
Nodes (6): IntegrationPublishConsumer, PublishRequestedMessage, JacksonPaymentEventPayloadConverter, PaymentEventPayloadConverter, PaymentEventConsumer, SmartReportStatusConsumer

### Community 184 - "qr/repository"
Cohesion: 0.14
Nodes (5): SmartReportGenerateMessage, QrDailyScan, QrListResponse, QrScanStore, QrPublicService

### Community 185 - "application/auth"
Cohesion: 0.18
Nodes (6): EmailVerificationAttemptGuard, IdentifierResolver, FindMerchantUseCase, MerchantProfileResponse, MerchantResponse, MerchantJpaRepository

### Community 187 - "order/service"
Cohesion: 0.18
Nodes (4): YemekSepetiOrder, YemekSepetiOrderRepository, KitchenYemekSepetiService, TypeReference<>

### Community 188 - "qrservice/security"
Cohesion: 0.17
Nodes (3): LegacyApiPathCompatFilter, RewrittenPathRequest, LegacyApiPathCompatFilterTest

### Community 189 - "application/service"
Cohesion: 0.27
Nodes (3): EntitlementUsageService, ListedEntitlement, UserEntitlement

### Community 192 - "resources"
Cohesion: 0.11
Nodes (19): health, shutdown, web, enabled, include, liveness-state, probes, readiness-state (+11 more)

### Community 193 - "shared/ui"
Cohesion: 0.16
Nodes (4): AlgoryQrTheme(), AppPalette, toColorScheme(), Tone

### Community 194 - "adminservice/dto"
Cohesion: 0.14
Nodes (6): DashboardAuthDtos, MeResponse, DashboardRole, ADMIN, UserAccessProfile, JwtServiceTest

### Community 198 - "order/dto"
Cohesion: 0.14
Nodes (16): CancelReason, CUSTOMER_REQUEST, LONG_WAIT, OTHER, OUT_OF_STOCK, PAYMENT_ISSUE, WRONG_ORDER, CancelOrderRequest (+8 more)

### Community 199 - "scripts"
Cohesion: 0.25
Nodes (12): api_json(), download_food_image(), enrich_description(), ensure_auth(), list_all_products(), login(), main(), nutrition_payload() (+4 more)

### Community 202 - "model/dto"
Cohesion: 0.21
Nodes (17): ChannelDailyPoint, ChannelShare, DailyRevenuePoint, FinanceAnalytics, HourlyRevenuePoint, MenuRevenueReportResponse, ProductsAnalytics, RevenueCategory (+9 more)

### Community 203 - "client/dto"
Cohesion: 0.19
Nodes (11): AiBatchReportClientDtos, CreateRequest, CreateResponse, Item, RequestCounts, ResultItem, StatusResponse, AiMenuImportClientDtos (+3 more)

### Community 205 - "domain/model"
Cohesion: 0.17
Nodes (4): BinInformation, InstallmentOption, InstallmentOptions, PaymentOptionsController

### Community 207 - "scripts"
Cohesion: 0.28
Nodes (14): create_branch(), create_qr_and_menu(), ensure_user(), find_existing_branch(), find_existing_menu(), main(), next_id(), now_utc() (+6 more)

### Community 210 - "store/service"
Cohesion: 0.20
Nodes (3): StoreHandle, StoreUrlBuilder, StoreUrlBuilderTest

### Community 212 - "platform/service"
Cohesion: 0.28
Nodes (5): QrVerificationMailClient, VerificationMail, NotificationRecipientsMessage, NotificationRequestMessage, NotificationPublisherService

### Community 214 - "model/dto"
Cohesion: 0.17
Nodes (14): AnalyticsCoverage, AnalyticsPeriod, CustomersAnalytics, DashboardAnalytics, FullAnalyticsResponse, OperationsTeaser, ShiftsAnalytics, ShiftSummaryRow (+6 more)

### Community 215 - "domain/model"
Cohesion: 0.18
Nodes (5): EntitlementSource, ADDON_PURCHASE, ONBOARDING_PACKAGE, PACKAGE_INCLUDE, FulfillmentCommand

### Community 216 - "docs/algoryqr-web-site"
Cohesion: 0.15
Nodes (5): ACCEPTED_TYPES, ProductImageField(), handleFileChange(), ProductImageFieldProps, ProductImageUploadResponse

### Community 217 - "scripts"
Cohesion: 0.40
Nodes (12): apply_menu_profile(), clear_stage_catalog(), clone_products(), clone_taxonomy(), ensure_stage_menu_id(), ensure_stage_menu_qr(), fetch_one_dict(), find_stage_menu_by_public_id() (+4 more)

### Community 218 - "internal/controller"
Cohesion: 0.24
Nodes (6): EmailRequest, InternalCredentialController, MerchantRequest, PasswordConfirmRequest, ReissueRequest, VerifyRequest

### Community 219 - "persistence/adapter"
Cohesion: 0.25
Nodes (4): CatalogProduct, ProductPort, ProductAdapter, ProductJpaRepository

### Community 220 - "billing/service"
Cohesion: 0.31
Nodes (3): ExternalPackageViewService, LoadedPackage, ExternalPackageViewServiceTest

### Community 222 - "qrservice/messaging"
Cohesion: 0.35
Nodes (4): AiCompletedMessage, AiError, AiProduct, IntegrationAiCompletedConsumer

### Community 224 - "model/enums"
Cohesion: 0.14
Nodes (12): AnalyticsEventItemRequest, AnalyticsEventsRequest, MenuAnalyticsEventType, ADD_TO_CART, CATEGORY_VIEW, CHECKOUT_START, MENU_OPEN, ORDER_SUBMITTED (+4 more)

### Community 225 - "print/model"
Cohesion: 0.15
Nodes (8): AckRequest, PrintJobStatus, CANCELLED, CLAIMED, DONE, FAILED, PENDING, PrintJobService

### Community 226 - "resources"
Cohesion: 0.14
Nodes (14): simple, host, listener, password, port, username, enabled, initial-interval (+6 more)

### Community 227 - "resources"
Cohesion: 0.14
Nodes (14): restaurant-menu-upsert, restaurant-menu-upsert-method, ubereats, base-url, connect-timeout, encrypt-key, max-attempts, paths (+6 more)

### Community 230 - "scripts"
Cohesion: 0.28
Nodes (9): assign_for_user(), ensure_schema(), fold_turkish(), load_descriptor_maps(), load_descriptors(), main(), resolve_descriptor_slug(), run_env() (+1 more)

### Community 231 - "scripts"
Cohesion: 0.40
Nodes (11): best_paradise_match(), brand_parts(), core_key(), fold(), load_alcoholic(), main(), normalize(), now() (+3 more)

### Community 234 - "analytics/dto"
Cohesion: 0.22
Nodes (10): AiReportSection, AiSmartReportResult, AiTokenUsage, Options, SmartReportAccepted, SmartReportCreateRequest, SmartReportDetailResponse, SmartReportDtos (+2 more)

### Community 238 - "model/dto"
Cohesion: 0.22
Nodes (10): AiReportSection, AiSmartReportResult, AiTokenUsage, Options, SmartReportAccepted, SmartReportCreateRequest, SmartReportDetailResponse, SmartReportDtos (+2 more)

### Community 241 - "resources"
Cohesion: 0.15
Nodes (13): driver-class-name, hikari, password, url, username, connection-timeout, idle-timeout, keepalive-time (+5 more)

### Community 242 - "resources"
Cohesion: 0.15
Nodes (12): app, url, jwt, access-expiration-ms, refresh-expiration-ms, secret, payment, service (+4 more)

### Community 243 - "domain/exception"
Cohesion: 0.21
Nodes (3): InvalidUsageRequestException, QuotaExceededException, ProductAccessView

### Community 244 - "scripts"
Cohesion: 0.40
Nodes (10): classify_drink_sub(), is_drink_name(), load_drink_subs(), main(), match_product(), normalize(), photo_drink_price(), setup_menu() (+2 more)

### Community 246 - "domain/model"
Cohesion: 0.24
Nodes (5): PlanPackageItem, ProductType, ADDON_PRODUCT, PACKAGE_PRODUCT, PackageItemResponse

### Community 247 - "staffservice/application"
Cohesion: 0.35
Nodes (5): AuthStaffClient, Command, UpsertStaffUseCase, InternalStaffController, UpsertRequest

### Community 250 - "domain/model"
Cohesion: 0.20
Nodes (10): PackageStatus, ACTIVE, CANCELLED, EXPIRED, GRACE_PERIOD, PAST_DUE, PENDING, REVOKED (+2 more)

### Community 251 - "analytics/dto"
Cohesion: 0.29
Nodes (5): BatchReportDetail, BatchReportDtos, BatchReportItemDetail, BatchReportListItem, RequestCounts

### Community 252 - "resources"
Cohesion: 0.20
Nodes (10): registration, authorization-grant-type, client-id, client-secret, redirect-uri, scope, client, google (+2 more)

### Community 254 - "scripts"
Cohesion: 0.61
Nodes (8): clone_menu(), clone_products(), clone_taxonomy(), ensure_stage_user(), fetch_one_dict(), insert_row(), main(), table_columns()

### Community 255 - "scripts"
Cohesion: 0.44
Nodes (8): classify_drink(), copy_category_covers(), main(), normalize(), resolve_aya_main(), setup_menu(), upsert_main(), upsert_sub()

### Community 258 - "resources"
Cohesion: 0.22
Nodes (8): odeal, base-url, default-vat-ratio, enabled, external-device-key, merchant-key, secret-key, test-api-key

### Community 263 - "model/dto"
Cohesion: 0.36
Nodes (6): MerchantStaffPerformanceReportResponse, StaffAnalytics, StaffRankingRow, WaiterPerformanceKpis, WaiterPerformanceProduct, WaiterPerformanceRow

### Community 264 - "model/dto"
Cohesion: 0.36
Nodes (7): DailyCount, NamedCount, RecordVisitRequest, SiteAnalyticsDtos, SummaryResponse, VisitItemResponse, VisitPageResponse

### Community 266 - "resources"
Cohesion: 0.25
Nodes (8): storage, access-key, bucket, filer-url, max-file-size-bytes, public-base-url, s3-endpoint, secret-key

### Community 267 - "resources"
Cohesion: 0.25
Nodes (8): odeal, base-url, default-vat-ratio, enabled, external-device-key, merchant-key, secret-key, test-api-key

### Community 268 - "resources"
Cohesion: 0.25
Nodes (8): paytr, base-url, debug-on, merchant-id, merchant-key, merchant-salt, test-mode, timeout-limit-minutes

### Community 269 - "resources"
Cohesion: 0.25
Nodes (8): name, ddl-auto, hibernate, open-in-view, show-sql, spring, application, jpa

### Community 270 - "resources"
Cohesion: 0.25
Nodes (8): hikari, connection-timeout, idle-timeout, keepalive-time, max-lifetime, maximum-pool-size, minimum-idle, validation-timeout

### Community 271 - "resources"
Cohesion: 0.25
Nodes (8): web, enabled, include, env, management, endpoints, info, exposure

### Community 272 - "resources"
Cohesion: 0.25
Nodes (8): ubereats-menu, api-base-url, auth-url, connect-timeout, default-scope, encrypt-key, max-attempts, read-timeout

### Community 273 - "resources"
Cohesion: 0.25
Nodes (8): yemeksepeti, base-url, connect-timeout, encrypt-key, max-attempts, poll-enabled, poll-lookback-hours, read-timeout

### Community 274 - "scripts"
Cohesion: 0.52
Nodes (6): apply_db(), flyway_version_exists(), main(), next_flyway_installed_rank(), record_flyway(), table_exists()

### Community 276 - "scripts"
Cohesion: 0.62
Nodes (5): now(), resolve_menu(), setup(), upsert_main(), upsert_sub()

### Community 281 - "model/dto"
Cohesion: 0.29
Nodes (6): CancellationByWaiter, CancellationsAnalytics, DailyOrderPoint, HourlyOrderPoint, OrdersAnalytics, StatusCount

### Community 284 - "resources"
Cohesion: 0.29
Nodes (7): seed, enabled, only-if-empty, chef-avatars, menu-products, menu-themes, taxonomy

### Community 285 - "resources"
Cohesion: 0.29
Nodes (7): billing, refund, subscription, monthly-cooling-days, pending-stuck-minutes, yearly-cooling-days, manual-payment-grace-days

### Community 287 - "authservice/controller"
Cohesion: 0.47
Nodes (3): DemoAuthController, DemoLoginRequest, DemoRegisterRequest

### Community 289 - "model/dto"
Cohesion: 0.53
Nodes (5): BatchReportDetail, BatchReportDtos, BatchReportItemDetail, BatchReportListItem, RequestCounts

### Community 290 - "model/dto"
Cohesion: 0.33
Nodes (5): EmailVerificationDtos, PublicVerifyRequest, ResendByEmailRequest, Status, VerifyRequest

### Community 291 - "resources"
Cohesion: 0.33
Nodes (6): chef-avatars, items, classpath-resource, key, label, object-key

### Community 292 - "resources"
Cohesion: 0.33
Nodes (6): exchange, routing-key, push-notification, channels, messaging, payment-subject

### Community 293 - "resources"
Cohesion: 0.33
Nodes (6): health, shutdown, probes, endpoint, enabled, access

### Community 294 - "resources"
Cohesion: 0.33
Nodes (6): enabled, ignore-migration-patterns, ignore-missing-migrations, out-of-order, validate-on-migrate, flyway

### Community 296 - "feature/reports"
Cohesion: 0.40
Nodes (5): StaffMetric, BASKET, ORDERS, REVENUE, TIPS

### Community 297 - "resources"
Cohesion: 0.40
Nodes (5): driver-class-name, password, url, username, datasource

### Community 298 - "resources"
Cohesion: 0.40
Nodes (5): liveness-state, readiness-state, enabled, health, enabled

### Community 300 - "feature/dashboard"
Cohesion: 0.50
Nodes (4): Period, MONTH, TODAY, WEEK

### Community 305 - "model/enums"
Cohesion: 0.50
Nodes (3): AccountingLineType, BILL, MANUAL

### Community 308 - "qrservice/config"
Cohesion: 0.67
Nodes (3): QuotaPeriod, DAY, WEEK

## Knowledge Gaps
- **527 isolated node(s):** `WAITER`, `KITCHEN`, `COURIER`, `PENDING`, `ACTIVE` (+522 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 2622 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **142 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `email-verification` connect `resources` to `presentation/controller`, `platform/service`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **What connects `WAITER`, `KITCHEN`, `COURIER` to the rest of the system?**
  _527 weakly-connected nodes found - possible missing edges._
- **Should `model/dto` be split into smaller, more focused modules?**
  _Cohesion score 0.012833808659579985 - nodes in this community are weakly interconnected._
- **Why does `app` connect `resources` to `resources`, `resources`, `resources`, `resources`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Should `model/enums` be split into smaller, more focused modules?**
  _Cohesion score 0.027057210031347964 - nodes in this community are weakly interconnected._
- **Why does `BadRequestException` connect `billing/service` to `model/enums`, `presentation/controller`, `model/enums`, `qrservice/service`, `qrservice/exception`, `qrservice/service`, `qrservice/service`, `branch/service`, `branch/service`, `qrservice/repository`, `billservice/application`, `analytics/service`, `menu/service`, `adminservice/application`, `authservice/controller`, `menu/controller`, `billing/service`, `model/enums`, `qrservice/coupon`, `analytics/service`, `order/service`, `billing/controller`, `integration/service`, `adminservice/application`, `orderflow/service`, `campaign/service`, `qrservice/model`, `branch/service`, `product/service`, `product/service`, `staffservice/application`, `menu/service`, `qrservice/service`, `service/ubereats`, `qrservice/service`, `billing/service`, `campaign/service`, `menu/service`, `service/yemeksepeti`, `qrservice/repository`, `qrservice/messaging`, `order/service`, `product/service`, `menu/service`, `billing/service`, `service/entitlement`, `print/service`, `menu/service`, `billing/service`, `integration/service`, `print/service`, `menu/service`, `staffservice/application`, `billing/service`, `service/ubereats`, `qrservice/config`, `model/enums`, `service/ubereatsmenu`, `qrservice/repository`, `yemeksepeti/mapper`, `platform/service`, `branch/service`, `campaign/service`, `customerservice/application`, `account/service`, `qrservice/service`, `analytics/service`, `order/service`, `staffservice/application`, `analytics/service`, `campaign/service`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Should `presentation/controller` be split into smaller, more focused modules?**
  _Cohesion score 0.016388105057521165 - nodes in this community are weakly interconnected._