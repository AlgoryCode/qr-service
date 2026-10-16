package com.ael.algoryqrservice.fulfillment;

import com.ael.algoryqrservice.access.SessionAccessPolicy;
import com.ael.algoryqrservice.access.SessionAccessService;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.client.PackageView;
import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.AssignedProduct;
import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.client.dto.ExternalConsumeResponse;
import com.ael.algoryqrservice.client.dto.ExternalEntitlementResponse;
import com.ael.algoryqrservice.client.dto.ExternalProductAccessResponse;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.BillingRefundProperties;
import com.ael.algoryqrservice.config.BillingSubscriptionProperties;
import com.ael.algoryqrservice.config.FulfillmentExternalProperties;
import com.ael.algoryqrservice.config.PackageExpirationScheduler;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.coupon.CouponRedemptionService;
import com.ael.algoryqrservice.event.PurchasesExpiredEvent;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.ConflictException;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.exception.FulfillmentQuotaExceededException;
import com.ael.algoryqrservice.messaging.fulfillment.FulfillmentQueuePublisher;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.PurchaseItem;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AddonPurchaseRequest;
import com.ael.algoryqrservice.model.dto.PaymentCompletedEventDto;
import com.ael.algoryqrservice.model.dto.BranchDtos;
import com.ael.algoryqrservice.model.enums.AccessDecision;
import com.ael.algoryqrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.model.enums.MenuPublicAccessDisabledReason;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.model.enums.SubscriptionStatus;
import com.ael.algoryqrservice.repository.PaymentEventInboxRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseItemRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.ProductUsageGateway;
import com.ael.algoryqrservice.service.AddonPurchaseService;
import com.ael.algoryqrservice.service.BillingAddressService;
import com.ael.algoryqrservice.service.BranchService;
import com.ael.algoryqrservice.service.CartPricingService;
import com.ael.algoryqrservice.service.ExternalPackageResponseMapper;
import com.ael.algoryqrservice.service.ExternalPackageViewService;
import com.ael.algoryqrservice.service.KitchenCloseGuard;
import com.ael.algoryqrservice.service.MenuPublicAccessService;
import com.ael.algoryqrservice.service.MenuQrSoftDeleteService;
import com.ael.algoryqrservice.service.PackagePricingService;
import com.ael.algoryqrservice.service.PaymentRequestMapper;
import com.ael.algoryqrservice.service.ProductImageStorageService;
import com.ael.algoryqrservice.service.PurchaseFulfillmentService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.entitlement.PurchaseSelectionPolicy;
import com.ael.algoryqrservice.service.PurchaseService;
import com.ael.algoryqrservice.purchase.lifecycle.RemoteSubscriptionCanceller;
import com.ael.algoryqrservice.service.SessionContextAssembler;
import com.ael.algoryqrservice.service.SessionContextService;
import com.ael.algoryqrservice.service.SubscriptionRefundPolicy;
import com.ael.algoryqrservice.service.UserDebtService;
import com.ael.algoryqrservice.service.UserPackageAssignmentService;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.util.AppTime;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.ael.algoryqrservice.util.WritableTransactionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FulfillmentLifecycleScenarioTest {

    private static final Long MERCHANT_ID = 7L;

    @Mock
    private ObjectProvider<FulfillmentServiceClient> fulfillmentClients;
    @Mock
    private FulfillmentServiceClient fulfillmentClient;
    @Mock
    private AuthMerchantClient authMerchantClient;
    @Mock
    private UserDebtService userDebtService;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PurchaseItemRepository purchaseItemRepository;
    @Mock
    private PurchaseLogService purchaseLogService;
    @Mock
    private BillingAddressService billingAddressService;
    @Mock
    private PaymentRequestMapper paymentRequestMapper;
    @Mock
    private PurchaseFulfillmentService purchaseFulfillmentService;
    @Mock
    private AppProperties appProperties;
    @Mock
    private PaymentClientProperties paymentClientProperties;
    @Mock
    private com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService purchaseExpiryService;
    @Mock
    private PurchaseSelectionPolicy purchaseSelectionPolicy;
    @Mock
    private FulfillmentQueuePublisher fulfillmentQueuePublisher;
    @Mock
    private PaymentEventInboxRepository paymentEventInboxRepository;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;
    @Mock
    private SubscriptionRefundPolicy subscriptionRefundPolicy;
    @Mock
    private BillingRefundProperties billingRefundProperties;
    @Mock
    private BillingSubscriptionProperties billingSubscriptionProperties;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private RemoteSubscriptionCanceller remoteSubscriptionCanceller;
    @Mock
    private CouponRedemptionService couponRedemptionService;
    @Mock
    private CartPricingService cartPricingService;
    @Mock
    private ExternalPackageViewService externalPackageView;
    @Mock
    private com.ael.algoryqrservice.repository.BranchRepository branchRepository;
    @Mock
    private com.ael.algoryqrservice.repository.MenuRepository menuRepository;
    @Mock
    private com.ael.algoryqrservice.repository.QrRepository qrRepository;
    @Mock
    private MenuQrSoftDeleteService menuQrSoftDeleteService;
    @Mock
    private ProductImageStorageService productImageStorageService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private KitchenCloseGuard kitchenCloseGuard;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PurchaseService purchaseService;

    private final ExternalPackageResponseMapper mapper = new ExternalPackageResponseMapper();
    private final PackagePricingService pricingService = new PackagePricingService();
    private UserPackageAssignmentService assignmentService;
    private AddonPurchaseService addonPurchaseService;
    private ProductUsageGateway productUsageGateway;
    private BranchService branchService;
    private SessionAccessService sessionAccessService;
    private SessionContextService sessionContextService;
    private ExternalPackageViewService packageViewService;
    private MenuPublicAccessService menuAccessService;
    private final SessionAccessPolicy sessionAccessPolicy = new SessionAccessPolicy();

    @BeforeEach
    void setUp() {
        when(fulfillmentClients.getIfAvailable()).thenReturn(fulfillmentClient);
        when(appProperties.getServiceName()).thenReturn("qr-service");
        when(securityUtils.getCurrentMerchantId()).thenReturn(MERCHANT_ID);
        assignmentService = new UserPackageAssignmentService(
                fulfillmentClients,
                authMerchantClient,
                userDebtService,
                planPackageRepository,
                pricingService
        );
        addonPurchaseService = new AddonPurchaseService(
                productRepository,
                purchaseRepository,
                purchaseItemRepository,
                purchaseLogService,
                billingAddressService,
                pricingService,
                paymentRequestMapper,
                paymentServiceClient,
                purchaseFulfillmentService,
                appProperties,
                paymentClientProperties,
                purchaseExpiryService
        );
        productUsageGateway = new ProductUsageGateway(fulfillmentClients);
        branchService = new BranchService(
                branchRepository,
                menuRepository,
                qrRepository,
                menuQrSoftDeleteService,
                fulfillmentClients,
                productUsageGateway,
                productImageStorageService,
                securityUtils,
                kitchenCloseGuard
        );
        sessionAccessService = new SessionAccessService(fulfillmentClients, mapper);
        sessionContextService = new SessionContextService(
                userRepository,
                fulfillmentClients,
                new SessionContextAssembler()
        );
        packageViewService = new ExternalPackageViewService(fulfillmentClients, mapper);
        menuAccessService = new MenuPublicAccessService(sessionAccessService, fulfillmentClients, menuRepository);
        when(userRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(User.builder().id(MERCHANT_ID).email("owner@example.com").build()));
        when(subscriptionRefundPolicy.refundEligibleUntil(any())).thenReturn(null);
    }

    @Test
    void assign_whenPaymentSkipped_thenWritesIncludedBranchRight() {
        PlanPackage plan = pricedPlan(CatalogPackages.PRO_PACKAGE, branchItem("QR_BRANCH", "Sube", 1));
        stubAssignableMerchant(plan);

        assignmentService.create(MERCHANT_ID, plan.getId(), null);

        verify(fulfillmentClient).createPackage(
                eq(MERCHANT_ID),
                eq(CatalogPackages.PRO_PACKAGE),
                eq(30),
                eq(List.of(new AssignedProduct("QR_BRANCH", "Sube", 1, false))),
                eq(List.of())
        );
        verify(paymentServiceClient, never()).initializeCheckoutForm(anyLong(), any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void assign_whenAccountInactive_thenDoesNotWrite() {
        when(authMerchantClient.findMerchant(MERCHANT_ID)).thenReturn(new AuthMerchantClient.MerchantView(MERCHANT_ID, "INACTIVE"));

        assertThatThrownBy(() -> assignmentService.create(MERCHANT_ID, 4L, null))
                .isInstanceOf(BadRequestException.class)
                .extracting(error -> ((BadRequestException) error).getCode())
                .isEqualTo(UserPackageAssignmentService.ACCOUNT_INACTIVE);
        verify(fulfillmentClient, never()).createPackage(anyLong(), anyString(), anyInt(), anyList(), anyList());
    }

    @Test
    void assign_whenPackageAlreadyActive_thenDoesNotWrite() {
        when(authMerchantClient.findMerchant(MERCHANT_ID)).thenReturn(new AuthMerchantClient.MerchantView(MERCHANT_ID, "ACTIVE"));
        when(fulfillmentClient.findPackage(MERCHANT_ID)).thenReturn(new PackageView("ACTIVE", activePackage("ACTIVE", daysFromToday(10))));

        assertThatThrownBy(() -> assignmentService.create(MERCHANT_ID, 4L, null))
                .isInstanceOf(ConflictException.class)
                .extracting(error -> ((ConflictException) error).getCode())
                .isEqualTo(UserPackageAssignmentService.PACKAGE_EXISTS);
        verify(fulfillmentClient, never()).createPackage(anyLong(), anyString(), anyInt(), anyList(), anyList());
    }

    @Test
    void assign_whenAccountInDebt_thenDoesNotWrite() {
        when(authMerchantClient.findMerchant(MERCHANT_ID)).thenReturn(new AuthMerchantClient.MerchantView(MERCHANT_ID, "ACTIVE"));
        when(fulfillmentClient.findPackage(MERCHANT_ID)).thenReturn(new PackageView("INACTIVE", null));
        when(userDebtService.findDebt(MERCHANT_ID)).thenReturn(Optional.of(new UserDebtService.DebtView(MERCHANT_ID, "OPEN")));

        assertThatThrownBy(() -> assignmentService.create(MERCHANT_ID, 4L, null))
                .isInstanceOf(BadRequestException.class)
                .extracting(error -> ((BadRequestException) error).getCode())
                .isEqualTo(UserPackageAssignmentService.ACCOUNT_IN_DEBT);
        verify(fulfillmentClient, never()).createPackage(anyLong(), anyString(), anyInt(), anyList(), anyList());
    }

    @Test
    void extraBranch_whenCheckoutStarted_thenStaysPendingWithoutFulfillment() {
        var started = startExtraBranchCheckout();

        assertThat(started.getStatus()).isEqualTo(PurchaseStatus.PENDING);
        verify(paymentServiceClient).initializeCheckoutForm(eq(MERCHANT_ID), any());
        verify(fulfillmentClient, never()).createPackage(anyLong(), anyString(), anyInt(), anyList(), anyList());
        verify(fulfillmentQueuePublisher, never()).publishGranted(any());
    }

    @Test
    void extraBranch_whenPaymentSkipped_thenRightIsUsable() {
        startExtraBranchCheckout();
        when(fulfillmentClient.findProductAccess(MERCHANT_ID, CatalogProducts.QR_BRANCH))
                .thenReturn(new ExternalProductAccessResponse(false, null, CatalogProducts.QR_BRANCH, null));

        assertThatCode(() -> productUsageGateway.use(MERCHANT_ID, CatalogProducts.QR_BRANCH, 1))
                .as("Odeme tamamlanmadan ek sube hakki kullanilabilir olmali")
                .doesNotThrowAnyException();
    }

    @Test
    void paymentSuccess_whenPaid_thenWritesPlanItemsAndAddonModules() {
        Purchase purchase = Purchase.builder()
                .id(10L)
                .userId(20L)
                .packageId(30L)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .packageName("Pro")
                .price(new BigDecimal("100.00"))
                .currency("TRY")
                .paymentConversationId("conversation-10")
                .paymentStyle(PaymentStyle.ONE_TIME)
                .installmentCount(1)
                .status(PurchaseStatus.PENDING)
                .build();
        PlanPackage plan = PlanPackage.builder()
                .id(30L)
                .code(CatalogPackages.PRO_PACKAGE)
                .validityDays(30)
                .items(List.of(
                        branchItem("QR_MENU", "Menu", 1),
                        branchItem("QR_BRANCH", "Sube", 1)
                ))
                .build();
        when(paymentEventInboxRepository.existsByEventId("event-1")).thenReturn(false);
        when(purchaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchase));
        when(planPackageRepository.findByIdWithItems(30L)).thenReturn(Optional.of(plan));
        when(purchaseItemRepository.findByPurchaseId(10L)).thenReturn(List.of(PurchaseItem.builder()
                .productCode(CatalogProducts.QR_BRANCH_ADDON)
                .productName("Ek sube")
                .quantity(2)
                .unlimited(false)
                .build()));

        purchaseService.handlePaymentSuccess(paidEvent(purchase));

        verify(fulfillmentClient).createPackage(
                eq(20L),
                eq(CatalogPackages.PRO_PACKAGE),
                eq(30),
                eq(List.of(
                        new AssignedProduct("QR_MENU", "Menu", 1, false),
                        new AssignedProduct("QR_BRANCH", "Sube", 1, false)
                )),
                eq(List.of(new AssignedProduct(CatalogProducts.QR_BRANCH_ADDON, "Ek sube", 2, false)))
        );
    }

    @Test
    void branch_whenRightDenied_thenNotCreated() {
        when(fulfillmentClient.findProductAccess(MERCHANT_ID, CatalogProducts.QR_BRANCH))
                .thenReturn(new ExternalProductAccessResponse(false, null, CatalogProducts.QR_BRANCH, null));

        assertThatThrownBy(() -> branchService.create(BranchDtos.CreateRequest.builder().name("Merkez").build()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bu ürün için aktif paket gerekli");
        verify(branchRepository, never()).save(any());
        verify(fulfillmentClient, never()).consume(anyLong(), anyString(), anyInt());
    }

    @Test
    void branch_whenQuotaExceeded_thenExtraBranchRequired() {
        allowBranch();
        when(fulfillmentClient.consume(MERCHANT_ID, CatalogProducts.QR_BRANCH, 1))
                .thenThrow(new FulfillmentQuotaExceededException());

        assertThatThrownBy(() -> branchService.create(BranchDtos.CreateRequest.builder().name("Merkez").build()))
                .isInstanceOf(ForbiddenException.class)
                .extracting(error -> ((ForbiddenException) error).getCode())
                .isEqualTo("EXTRA_BRANCH_REQUIRED");
        verify(branchRepository, never()).save(any());
    }

    @Test
    void branch_whenRightOpen_thenConsumesAndCreates() {
        allowBranch();
        when(fulfillmentClient.consume(MERCHANT_ID, CatalogProducts.QR_BRANCH, 1))
                .thenReturn(new ExternalConsumeResponse(1L, 10L, 1, 0, false));
        when(branchRepository.save(any())).thenAnswer(invocation -> {
            com.ael.algoryqrservice.model.Branch branch = invocation.getArgument(0);
            branch.setId(3L);
            return branch;
        });

        BranchDtos.Response created = branchService.create(BranchDtos.CreateRequest.builder().name("Merkez").build());

        assertThat(created.getId()).isEqualTo(3L);
        verify(fulfillmentClient).consume(MERCHANT_ID, CatalogProducts.QR_BRANCH, 1);
    }

    @Test
    void branchQuota_whenRemainingChanges_thenCanCreateFollowsRemaining() {
        when(branchRepository.countByUserIdAndDeletedFalse(MERCHANT_ID)).thenReturn(2L);
        when(branchRepository.findByUserIdAndDeletedFalseOrderByIdDesc(MERCHANT_ID)).thenReturn(List.of());
        when(menuRepository.findByUserIdAndChannelAndDeletedFalseOrderByMenuIdAsc(MERCHANT_ID, MenuChannel.QR)).thenReturn(List.of());
        when(fulfillmentClient.remaining(MERCHANT_ID, CatalogProducts.QR_MENU)).thenReturn(0);
        when(fulfillmentClient.remaining(MERCHANT_ID, CatalogProducts.QR_BRANCH)).thenReturn(0);

        BranchDtos.Quota empty = branchService.listMine().getQuota();

        assertThat(empty.getUsed()).isEqualTo(2);
        assertThat(empty.getRemaining()).isZero();
        assertThat(empty.getAllowed()).isEqualTo(2);
        assertThat(empty.isCanCreate()).isFalse();
        assertThat(empty.getGrandfathered()).isZero();
        assertThat(empty.getExtraPurchased()).isZero();

        when(fulfillmentClient.remaining(MERCHANT_ID, CatalogProducts.QR_BRANCH)).thenReturn(3);
        BranchDtos.Quota open = branchService.listMine().getQuota();

        assertThat(open.getRemaining()).isEqualTo(3);
        assertThat(open.getAllowed()).isEqualTo(5);
        assertThat(open.isCanCreate()).isTrue();
        assertThat(open.getExtraPurchased()).isZero();
        assertThat(open.getGrandfathered()).isZero();
    }

    @Test
    void display_whenPackageAndRightsLoaded_thenQuantitiesAndStatusMatch() {
        LocalDate periodEnd = daysFromToday(10);
        when(fulfillmentClient.findPackage(MERCHANT_ID))
                .thenReturn(new PackageView("ACTIVE", activePackage("ACTIVE", periodEnd)));
        when(fulfillmentClient.listEntitlements(MERCHANT_ID)).thenReturn(List.of(
                entitlement(1L, CatalogProducts.QR_BRANCH, 3, 1, null),
                entitlement(2L, CatalogProducts.QR_BRANCH, 2, 2, Instant.now().minusSeconds(60))
        ));

        var entitlements = packageViewService.entitlements(MERCHANT_ID).orElseThrow();
        var overview = packageViewService.overview(MERCHANT_ID).orElseThrow();

        assertThat(entitlements.getFirst().getRemainingQuantity()).isEqualTo(2);
        assertThat(entitlements.getFirst().getTotalQuantity()).isEqualTo(3);
        assertThat(entitlements.getFirst().getUsedQuantity()).isEqualTo(1);
        assertThat(entitlements.getFirst().isUsable()).isTrue();
        assertThat(entitlements.get(1).isUsable()).isFalse();
        assertThat(entitlements.get(1).isExpired()).isTrue();
        assertThat(entitlements.get(1).getPurchaseStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        assertThat(entitlements.get(1).getRemainingQuantity()).isZero();
        assertThat(overview.getFulfillmentDetails().getFirst().getRemainingQuantity()).isEqualTo(2);
        assertThat(overview.getActivePackage().getPackageCode()).isEqualTo(CatalogPackages.PRO_PACKAGE);
        assertThat(overview.getActivePackage().getPackageName()).isEqualTo("Pro");
        assertThat(overview.getActivePackage().getDaysUntilExpiry()).isEqualTo(10);
        assertThat(overview.getActivePackage().isUsable()).isTrue();
        assertThat(overview.getActivePackage().isExpired()).isFalse();
        assertThat(overview.getAddonPurchases()).isEmpty();
        assertThat(overview.isFulfillmentActive()).isTrue();
        assertThat(mapper.toPurchase(activePackage("DEMO", periodEnd)).getPurchaseType()).isEqualTo(PurchaseType.TRIAL);
        assertThat(mapper.toPurchase(activePackage("DEMO", periodEnd)).isUsable()).isTrue();
    }

    @Test
    void session_whenPackageInactive_thenEntryIsClosed() {
        when(fulfillmentClient.findPackage(MERCHANT_ID)).thenReturn(new PackageView("INACTIVE", null));
        when(fulfillmentClient.findProductAccess(MERCHANT_ID, CatalogProducts.QR_BRANCH))
                .thenReturn(new ExternalProductAccessResponse(false, null, CatalogProducts.QR_BRANCH, null));

        assertThat(sessionAccessService.resolve(MERCHANT_ID).decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);
        assertThat(sessionContextService.resolve(MERCHANT_ID).decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);
        assertThat(packageViewService.purchases(MERCHANT_ID).orElseThrow()).isEmpty();
        assertThat(menuAccessService.evaluate(MERCHANT_ID).allowed()).isFalse();
        assertThat(menuAccessService.evaluate(MERCHANT_ID).reason()).isEqualTo(MenuPublicAccessDisabledReason.PACKAGE_INACTIVE);
        assertThatThrownBy(() -> productUsageGateway.allow(MERCHANT_ID, CatalogProducts.QR_BRANCH))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void session_whenFulfillmentActiveDespiteLocalSuspend_thenAllow() {
        Purchase suspended = Purchase.builder()
                .id(12L)
                .userId(MERCHANT_ID)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .status(PurchaseStatus.ACTIVE)
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .subscriptionStatus(SubscriptionStatus.SUSPENDED)
                .startsAt(LocalDateTime.now().minusDays(5))
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
        when(fulfillmentClient.findPackage(MERCHANT_ID))
                .thenReturn(new PackageView("ACTIVE", activePackage("ACTIVE", daysFromToday(10))));
        when(fulfillmentClient.findProductAccess(MERCHANT_ID, CatalogProducts.QR_MENU))
                .thenReturn(new ExternalProductAccessResponse(true, CatalogPackages.PRO_PACKAGE, CatalogProducts.QR_MENU, null));
        when(fulfillmentClient.listEntitlements(MERCHANT_ID)).thenReturn(List.of());

        assertThat(suspended.isUsable()).isFalse();
        assertThat(sessionAccessPolicy.decide(Optional.of(suspended)).decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);
        assertThat(sessionAccessService.resolve(MERCHANT_ID).decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(sessionContextService.resolve(MERCHANT_ID).decision()).isEqualTo(AccessDecision.ALLOW);
        assertThat(sessionContextService.resolve(MERCHANT_ID).packageCode()).isEqualTo(CatalogPackages.PRO_PACKAGE);
        assertThat(menuAccessService.evaluate(MERCHANT_ID).allowed()).isTrue();
    }

    @Test
    void grace_whenExpired_thenSuspendsWithoutRevoke() {
        Purchase grace = Purchase.builder()
                .id(13L)
                .userId(MERCHANT_ID)
                .packageName("Pro")
                .status(PurchaseStatus.ACTIVE)
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .subscriptionStatus(SubscriptionStatus.GRACE_PERIOD)
                .subscriptionGraceEndsAt(LocalDateTime.now().minusMinutes(5))
                .build();
        when(purchaseRepository.findByStatusAndExpiresAtBefore(eq(PurchaseStatus.ACTIVE), any())).thenReturn(List.of());
        when(purchaseRepository.findByPaymentStyleAndSubscriptionStatusAndSubscriptionGraceEndsAtBefore(
                eq(PaymentStyle.SUBSCRIPTION), eq(SubscriptionStatus.GRACE_PERIOD), any()
        )).thenReturn(List.of(grace));
        when(purchaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        scheduler().expirePackages();

        assertThat(grace.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(grace.getSubscriptionStatusReason()).isEqualTo("grace_period_expired");
        verify(fulfillmentQueuePublisher, never()).publishRevoked(any());
        verify(menuPublicAccessService).syncForUser(MERCHANT_ID);
    }

    @Test
    void extend_whenAdminReactivates_thenGrantsAndSessionFollowsFulfillment() {
        Purchase purchase = Purchase.builder()
                .id(11L)
                .userId(MERCHANT_ID)
                .packageId(30L)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .packageName("Pro")
                .status(PurchaseStatus.EXPIRED)
                .purchaseType(PurchaseType.PAID)
                .paymentStyle(PaymentStyle.ONE_TIME)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .currency("TRY")
                .price(new BigDecimal("100.00"))
                .build();
        when(purchaseRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.findById(11L)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseRepository.findByUserIdAndStatusAndPurchaseType(MERCHANT_ID, PurchaseStatus.ACTIVE, PurchaseType.ADD_ON))
                .thenReturn(List.of());
        when(fulfillmentClient.findPackage(MERCHANT_ID)).thenReturn(new PackageView("INACTIVE", null));

        var extended = purchaseService.extendSubscriptionForAdmin(11L, 15);

        assertThat(extended.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        assertThat(purchase.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(purchase.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(fulfillmentQueuePublisher).publishGranted(purchase);
        verify(fulfillmentClient, never()).createPackage(anyLong(), anyString(), anyInt(), anyList(), anyList());
        verify(menuPublicAccessService).syncForUser(MERCHANT_ID);
        assertThat(sessionAccessService.resolve(MERCHANT_ID).decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);

        when(fulfillmentClient.findPackage(MERCHANT_ID))
                .thenReturn(new PackageView("ACTIVE", activePackage("ACTIVE", daysFromToday(15))));
        assertThat(sessionAccessService.resolve(MERCHANT_ID).decision()).isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void periodEnd_whenYesterday_thenPackageInactiveAndBranchDenied() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FulfillmentExternalProperties properties = new FulfillmentExternalProperties();
        properties.setBaseUrl("http://fulfillment.test");
        properties.setAuthHeader("X-Service-Token");
        properties.setAuthToken("token");
        FulfillmentServiceClient client = new FulfillmentServiceClient(builder, properties);
        ObjectProvider<FulfillmentServiceClient> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        SessionAccessService sessions = new SessionAccessService(provider, mapper);
        ExternalPackageViewService views = new ExternalPackageViewService(provider, mapper);
        ProductUsageGateway gateway = new ProductUsageGateway(provider);
        BranchService branches = new BranchService(
                branchRepository,
                menuRepository,
                qrRepository,
                menuQrSoftDeleteService,
                provider,
                gateway,
                productImageStorageService,
                securityUtils,
                kitchenCloseGuard
        );
        String packageUrl = "http://fulfillment.test/api/v1/users/7/package";
        String yesterday = daysFromToday(-1).toString();
        String body = """
                {"id":1,"userId":7,"fulfillmentId":1,"purchaseId":10,"packageId":30,"packageCode":"PRO_PACKAGE","packageName":"Pro","periodStart":"2026-09-01","periodEnd":"%s","status":"ACTIVE"}
                """.formatted(yesterday);
        server.expect(requestTo(packageUrl)).andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(packageUrl)).andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(packageUrl)).andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://fulfillment.test/api/v1/users/7/products/QR_BRANCH"))
                .andRespond(withSuccess(
                        "{\"allowed\":false,\"packageCode\":null,\"featureCode\":\"QR_BRANCH\",\"scopeCode\":null}",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(client.findPackage(MERCHANT_ID).status()).isEqualTo("INACTIVE");
        assertThat(sessions.resolve(MERCHANT_ID).decision()).isEqualTo(AccessDecision.REQUIRE_PURCHASE);
        assertThat(views.purchases(MERCHANT_ID).orElseThrow()).isEmpty();
        assertThatThrownBy(() -> branches.create(BranchDtos.CreateRequest.builder().name("Merkez").build()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bu ürün için aktif paket gerekli");
        verify(branchRepository, never()).save(any());
        server.verify();
    }

    @Test
    void scheduler_whenPurchaseDue_thenRevokesThenExpires() {
        Purchase due = Purchase.builder()
                .id(4L)
                .userId(MERCHANT_ID)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .packageName("Pro")
                .build();
        when(purchaseRepository.findByStatusAndExpiresAtBefore(eq(PurchaseStatus.ACTIVE), any())).thenReturn(List.of(due));
        when(purchaseRepository.findByPaymentStyleAndSubscriptionStatusAndSubscriptionGraceEndsAtBefore(
                eq(PaymentStyle.SUBSCRIPTION), eq(SubscriptionStatus.GRACE_PERIOD), any()
        )).thenReturn(List.of());
        when(purchaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        scheduler().expirePackages();

        assertThat(due.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        InOrder order = inOrder(fulfillmentQueuePublisher, purchaseRepository);
        order.verify(fulfillmentQueuePublisher).publishRevoked(due);
        order.verify(purchaseRepository).save(due);
        verify(eventPublisher).publishEvent(any(PurchasesExpiredEvent.class));
        verify(menuPublicAccessService).syncForUsers(List.of(MERCHANT_ID));
    }

    @Test
    void readPath_whenDue_thenExpiresWithoutRevoke() {
        Purchase due = Purchase.builder()
                .id(5L)
                .userId(MERCHANT_ID)
                .status(PurchaseStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().minusHours(2))
                .packageName("Pro")
                .build();
        when(purchaseRepository.findByUserIdAndStatusAndExpiresAtBefore(eq(MERCHANT_ID), eq(PurchaseStatus.ACTIVE), any()))
                .thenReturn(List.of(due));
        when(purchaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PurchaseExpiryService expiry = new PurchaseExpiryService(
                purchaseRepository,
                purchaseLogService,
                eventPublisher,
                new WritableTransactionGuard()
        );

        expiry.expireDueForUser(MERCHANT_ID);

        assertThat(due.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        verify(fulfillmentQueuePublisher, never()).publishRevoked(any());
        verify(eventPublisher).publishEvent(any(PurchasesExpiredEvent.class));
    }

    private PackageExpirationScheduler scheduler() {
        PurchaseExpiryService expiry = new PurchaseExpiryService(
                purchaseRepository,
                purchaseLogService,
                eventPublisher,
                new WritableTransactionGuard()
        );
        return new PackageExpirationScheduler(
                expiry,
                menuPublicAccessService,
                purchaseRepository,
                fulfillmentQueuePublisher
        );
    }

    private com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse startExtraBranchCheckout() {
        Purchase host = Purchase.builder()
                .id(8L)
                .userId(MERCHANT_ID)
                .packageId(30L)
                .packageCode(CatalogPackages.PRO_PACKAGE)
                .status(PurchaseStatus.ACTIVE)
                .purchaseType(PurchaseType.PAID)
                .paymentStyle(PaymentStyle.ONE_TIME)
                .expiresAt(LocalDateTime.now().plusDays(40))
                .build();
        Product addon = Product.builder()
                .id(9L)
                .code(CatalogProducts.QR_BRANCH_ADDON)
                .name("Ek sube")
                .unitPrice(new BigDecimal("50.00"))
                .vatRate(new BigDecimal("20.00"))
                .active(true)
                .addonPurchasable(true)
                .consumable(true)
                .build();
        when(purchaseRepository.findByUserIdAndStatus(MERCHANT_ID, PurchaseStatus.ACTIVE)).thenReturn(List.of(host));
        when(productRepository.findByCode(CatalogProducts.QR_BRANCH_ADDON)).thenReturn(Optional.of(addon));
        when(purchaseRepository.save(any())).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            if (purchase.getId() == null) {
                purchase.setId(50L);
            }
            return purchase;
        });
        when(purchaseItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRequestMapper.newPaymentAttemptId(MERCHANT_ID)).thenReturn("attempt-1");
        when(paymentRequestMapper.toAddonCartCheckoutFormRequest(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(PaymentCheckoutFormRequest.builder().build());
        PaymentCheckoutFormResponse checkout = new PaymentCheckoutFormResponse();
        checkout.setConversationId("conv-addon");
        checkout.setToken("token");
        when(paymentServiceClient.initializeCheckoutForm(eq(MERCHANT_ID), any())).thenReturn(checkout);
        AddonPurchaseRequest request = new AddonPurchaseRequest();
        request.setProductCode(CatalogProducts.QR_BRANCH_ADDON);
        request.setQuantity(1);
        request.setBillingAddressId(1L);
        return addonPurchaseService.purchase(User.builder().id(MERCHANT_ID).email("owner@example.com").build(), request, "127.0.0.1");
    }

    private void stubAssignableMerchant(PlanPackage plan) {
        when(authMerchantClient.findMerchant(MERCHANT_ID)).thenReturn(new AuthMerchantClient.MerchantView(MERCHANT_ID, "ACTIVE"));
        when(fulfillmentClient.findPackage(MERCHANT_ID)).thenReturn(new PackageView("INACTIVE", null));
        when(userDebtService.findDebt(MERCHANT_ID)).thenReturn(Optional.empty());
        when(planPackageRepository.findByIdWithItems(plan.getId())).thenReturn(Optional.of(plan));
    }

    private PlanPackage pricedPlan(String code, PlanPackageItem item) {
        PlanPackage plan = PlanPackage.builder()
                .id(4L)
                .code(code)
                .name("Pro")
                .currency("TRY")
                .active(true)
                .validityDays(30)
                .purchasable(true)
                .systemManaged(false)
                .items(List.of(item))
                .build();
        PackagePricingService.PriceBreakdown breakdown = pricingService.calculate(plan.getItems());
        plan.setSubtotal(breakdown.subtotal());
        plan.setVatAmount(breakdown.vatAmount());
        plan.setPrice(breakdown.total());
        return plan;
    }

    private PlanPackageItem branchItem(String code, String name, int quantity) {
        Product product = Product.builder()
                .id(1L)
                .code(code)
                .name(name)
                .unitPrice(new BigDecimal("100.00"))
                .vatRate(new BigDecimal("20.00"))
                .active(true)
                .build();
        return PlanPackageItem.builder()
                .product(product)
                .quantity(quantity)
                .unlimited(false)
                .build();
    }

    private void allowBranch() {
        when(fulfillmentClient.findProductAccess(MERCHANT_ID, CatalogProducts.QR_BRANCH))
                .thenReturn(new ExternalProductAccessResponse(true, CatalogPackages.PRO_PACKAGE, CatalogProducts.QR_BRANCH, null));
        when(fulfillmentClient.listEntitlements(MERCHANT_ID))
                .thenReturn(List.of(entitlement(1L, CatalogProducts.QR_BRANCH, 1, 0, null)));
    }

    private ExternalEntitlementResponse entitlement(
            Long id,
            String code,
            int quantity,
            int used,
            Instant expiresAt
    ) {
        return new ExternalEntitlementResponse(
                id,
                MERCHANT_ID,
                id,
                10L,
                1L,
                "FEATURE",
                code,
                code + "_OWNER",
                quantity,
                false,
                used,
                "PACKAGE_INCLUDE",
                "ACTIVE",
                null,
                expiresAt,
                code
        );
    }

    private ExternalActivePackageResponse activePackage(String status, LocalDate periodEnd) {
        return new ExternalActivePackageResponse(
                1L,
                MERCHANT_ID,
                1L,
                10L,
                30L,
                CatalogPackages.PRO_PACKAGE,
                "Pro",
                daysFromToday(-5),
                periodEnd,
                status,
                Instant.now(),
                Instant.now(),
                List.of(CatalogProducts.QR_BRANCH),
                List.of("QR_BRANCH_OWNER")
        );
    }

    private PaymentCompletedEventDto paidEvent(Purchase purchase) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("purchaseId", String.valueOf(purchase.getId()));
        metadata.put("userId", String.valueOf(purchase.getUserId()));
        metadata.put("packageId", String.valueOf(purchase.getPackageId()));
        metadata.put("packageCode", purchase.getPackageCode());
        metadata.put("purchaseConversationId", purchase.getPaymentConversationId());
        metadata.put("installmentId", "inst-1");
        metadata.put("installmentNumber", 1);
        metadata.put("installmentCount", 1);
        metadata.put("periodStart", "2026-10-01T00:00:00");
        metadata.put("periodEnd", "2026-11-01T00:00:00");
        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setEventId("event-1");
        event.setEventType("payment.success");
        event.setServiceName("qr-service");
        event.setCurrency("TRY");
        event.setAmount(purchase.getPrice());
        event.setSourceMetadata(metadata);
        return event;
    }

    private LocalDate daysFromToday(int days) {
        return LocalDate.now(AppTime.ZONE).plusDays(days);
    }
}
