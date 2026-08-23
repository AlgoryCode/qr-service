package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.PlanChange;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PlanChangePreviewResponse;
import com.ael.algoryqrservice.model.dto.PlanChangeRequest;
import com.ael.algoryqrservice.model.dto.PlanChangeResponse;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PlanChangeDirection;
import com.ael.algoryqrservice.model.enums.PlanChangeStatus;
import com.ael.algoryqrservice.model.enums.PlanChangeTiming;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.PlanChangeRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanChangeServiceTest {

    @Mock
    private PlanChangeRepository planChangeRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private PlanPackageRepository planPackageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlanPackageService planPackageService;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private PaymentRequestMapper paymentRequestMapper;
    @Mock
    private PurchaseFulfillmentService purchaseFulfillmentService;
    @Mock
    private PackageActivationService packageActivationService;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private MenuPublicAccessService menuPublicAccessService;
    @Mock
    private PurchaseLogService purchaseLogService;
    @Mock
    private AppProperties appProperties;
    @Mock
    private PaymentClientProperties paymentClientProperties;

    @InjectMocks
    private PlanChangeService planChangeService;

    private User user;
    private Purchase currentPurchase;
    private PlanPackage starter;
    private PlanPackage pro;
    private BillingSnapshot billingSnapshot;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(10L)
                .email("user@test.com")
                .firstName("Ali")
                .lastName("Veli")
                .phone("555")
                .build();

        billingSnapshot = BillingSnapshot.builder()
                .name("Ali")
                .surname("Veli")
                .address("Adres")
                .city("Istanbul")
                .country("TR")
                .postcode("34000")
                .tckn("11111111111")
                .build();

        currentPurchase = Purchase.builder()
                .id(100L)
                .userId(10L)
                .packageId(1L)
                .packageCode("STARTER")
                .packageName("Starter")
                .price(new BigDecimal("100.00"))
                .currency("TRY")
                .status(PurchaseStatus.ACTIVE)
                .purchaseType(PurchaseType.PAID)
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .paymentMethodId(55L)
                .paymentConversationId("paid-conv-100")
                .billingSnapshot(billingSnapshot)
                .startsAt(LocalDateTime.now().minusDays(5))
                .expiresAt(LocalDateTime.now().plusDays(25))
                .build();

        Product product = Product.builder()
                .id(7L)
                .code("QR_CREATE")
                .name("QR")
                .build();
        PlanPackageItem item = PlanPackageItem.builder()
                .id(1L)
                .product(product)
                .quantity(50)
                .unlimited(false)
                .build();

        starter = PlanPackage.builder()
                .id(1L)
                .code("STARTER")
                .name("Starter")
                .price(new BigDecimal("100.00"))
                .currency("TRY")
                .validityDays(30)
                .purchasable(true)
                .active(true)
                .items(List.of())
                .build();

        pro = PlanPackage.builder()
                .id(2L)
                .code("PRO")
                .name("Pro")
                .price(new BigDecimal("300.00"))
                .currency("TRY")
                .validityDays(30)
                .purchasable(true)
                .active(true)
                .items(List.of(item))
                .features(List.of("50 QR", "Oncelikli destek"))
                .build();
    }

    @Test
    void preview_whenUpgrade_thenDirectionUpgradeAndOptionsPresent() {
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(currentPurchase));
        when(planPackageService.findPackage(1L)).thenReturn(starter);
        when(planPackageService.findActivePackage(2L)).thenReturn(pro);
        when(planChangeRepository.existsByUserIdAndStatus(10L, PlanChangeStatus.SCHEDULED)).thenReturn(false);

        PlanChangePreviewResponse preview = planChangeService.preview(10L, 2L);

        assertThat(preview.getDirection()).isEqualTo(PlanChangeDirection.UPGRADE);
        assertThat(preview.getOptions()).hasSize(2);
        assertThat(preview.getOptions().getFirst().getTiming()).isEqualTo(PlanChangeTiming.IMMEDIATE);
        assertThat(preview.getOptions().getFirst().getChargeNow()).isGreaterThan(BigDecimal.ZERO);
        assertThat(preview.getOptions().getFirst().getChargeNow()).isLessThanOrEqualTo(new BigDecimal("200.00"));
        assertThat(preview.getOptions().getFirst().getRefundNow()).isEqualByComparingTo("0");
        assertThat(preview.getOptions().get(1).getTiming()).isEqualTo(PlanChangeTiming.NEXT_PERIOD);
        assertThat(preview.getOptions().get(1).getChargeNow()).isEqualByComparingTo("0");
        assertThat(preview.getOptions().get(1).getChargeAtEffective()).isEqualByComparingTo("300.00");
        assertThat(preview.getWarnings()).isNotEmpty();
    }

    @Test
    void preview_whenDowngrade_thenNextPeriodOnly() {
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(Purchase.builder()
                        .id(101L)
                        .userId(10L)
                        .packageId(2L)
                        .packageCode("PRO")
                        .packageName("Pro")
                        .price(new BigDecimal("300.00"))
                        .currency("TRY")
                        .status(PurchaseStatus.ACTIVE)
                        .purchaseType(PurchaseType.PAID)
                        .paymentStyle(PaymentStyle.SUBSCRIPTION)
                        .paymentConversationId("paid-conv-pro")
                        .expiresAt(LocalDateTime.now().plusDays(20))
                        .build()));
        when(planPackageService.findPackage(2L)).thenReturn(pro);
        when(planPackageService.findActivePackage(1L)).thenReturn(starter);
        when(planChangeRepository.existsByUserIdAndStatus(10L, PlanChangeStatus.SCHEDULED)).thenReturn(false);

        PlanChangePreviewResponse preview = planChangeService.preview(10L, 1L);

        assertThat(preview.getDirection()).isEqualTo(PlanChangeDirection.DOWNGRADE);
        assertThat(preview.getOptions()).hasSize(1);
        assertThat(preview.getOptions().getFirst().getTiming()).isEqualTo(PlanChangeTiming.NEXT_PERIOD);
        assertThat(preview.getOptions().getFirst().getChargeNow()).isEqualByComparingTo("0");
        assertThat(preview.getOptions().getFirst().getRefundNow()).isEqualByComparingTo("0");
        assertThat(preview.getWarnings()).anyMatch(w -> w.contains("donem sonunda"));
    }

    @Test
    void request_whenNextPeriod_thenScheduled() {
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(currentPurchase));
        when(planPackageService.findPackage(1L)).thenReturn(starter);
        when(planPackageService.findActivePackage(2L)).thenReturn(pro);
        when(planChangeRepository.existsByUserIdAndStatus(10L, PlanChangeStatus.SCHEDULED)).thenReturn(false);
        when(planChangeRepository.existsByUserIdAndStatus(10L, PlanChangeStatus.PENDING_PAYMENT)).thenReturn(false);
        when(paymentServiceClient.getPaymentMethods(10L)).thenReturn(List.of(
                new BillingPaymentDtos.PaymentMethod("55", "Kart", "visa", "4242", 12, 2030)
        ));
        when(planChangeRepository.save(any(PlanChange.class))).thenAnswer(invocation -> {
            PlanChange pc = invocation.getArgument(0);
            pc.setId(9L);
            return pc;
        });
        when(planPackageRepository.findById(1L)).thenReturn(Optional.of(starter));
        when(planPackageRepository.findById(2L)).thenReturn(Optional.of(pro));

        PlanChangeRequest request = new PlanChangeRequest();
        request.setToPackageId(2L);
        request.setTiming(PlanChangeTiming.NEXT_PERIOD);
        request.setPaymentMethodId(55L);
        request.setWarningAck(true);

        PlanChangeResponse response = planChangeService.request(user, request, "1.1.1.1");

        assertThat(response.getStatus()).isEqualTo(PlanChangeStatus.SCHEDULED);
        assertThat(response.getTiming()).isEqualTo(PlanChangeTiming.NEXT_PERIOD);
        assertThat(response.getDirection()).isEqualTo(PlanChangeDirection.UPGRADE);
        verify(paymentServiceClient, never()).initializeCheckoutForm(anyLong(), any());
        verify(paymentServiceClient, never()).chargeStoredCard(anyLong(), any());
    }

    @Test
    void request_whenImmediate_thenStartsPaytrCheckout() {
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(currentPurchase));
        when(planPackageService.findPackage(1L)).thenReturn(starter);
        when(planPackageService.findActivePackage(2L)).thenReturn(pro);
        when(planChangeRepository.existsByUserIdAndStatus(anyLong(), any())).thenReturn(false);
        when(paymentServiceClient.getPaymentMethods(10L)).thenReturn(List.of(
                new BillingPaymentDtos.PaymentMethod("55", "Kart", "visa", "4242", 12, 2030)
        ));
        when(appProperties.getServiceName()).thenReturn("qr-service");
        when(paymentRequestMapper.newPaymentAttemptId(anyLong())).thenReturn("conv-1");
        PaymentCheckoutFormRequest checkoutRequest = PaymentCheckoutFormRequest.builder().build();
        when(paymentRequestMapper.toPlanChangeCheckoutFormRequest(
                any(), eq(user), eq(pro), eq("1.1.1.1"), eq(appProperties), eq(paymentClientProperties),
                eq("conv-1"), any()
        )).thenReturn(checkoutRequest);
        PaymentCheckoutFormResponse checkoutResponse = new PaymentCheckoutFormResponse();
        checkoutResponse.setConversationId("conv-1");
        checkoutResponse.setToken("paytr-token");
        checkoutResponse.setPaymentPageUrl("https://www.paytr.com/odeme/guvenli/paytr-token");
        checkoutResponse.setCheckoutFormContent("<iframe></iframe>");
        when(paymentServiceClient.initializeCheckoutForm(10L, checkoutRequest)).thenReturn(checkoutResponse);
        when(planChangeRepository.save(any(PlanChange.class))).thenAnswer(invocation -> {
            PlanChange pc = invocation.getArgument(0);
            if (pc.getId() == null) {
                pc.setId(11L);
            }
            return pc;
        });
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(200L);
            }
            return p;
        });
        when(planPackageRepository.findById(1L)).thenReturn(Optional.of(starter));
        when(planPackageRepository.findById(2L)).thenReturn(Optional.of(pro));

        PlanChangeRequest request = new PlanChangeRequest();
        request.setToPackageId(2L);
        request.setTiming(PlanChangeTiming.IMMEDIATE);
        request.setPaymentMethodId(55L);
        request.setWarningAck(true);

        PlanChangeResponse response = planChangeService.request(user, request, "1.1.1.1");

        assertThat(response.getStatus()).isEqualTo(PlanChangeStatus.PENDING_PAYMENT);
        assertThat(response.getToken()).isEqualTo("paytr-token");
        assertThat(response.getPaymentPageUrl()).contains("paytr.com");
        assertThat(response.getCheckoutFormContent()).contains("iframe");
        verify(paymentServiceClient).initializeCheckoutForm(eq(10L), any());
        verify(paymentServiceClient, never()).refundPayment(anyLong(), anyString(), any(), anyString());
        verify(entitlementService, never()).grant(any(), anyLong(), anyString(), anyInt(), anyBoolean());
        verify(packageActivationService, never()).activatePurchasedPackage(any());
    }

    @Test
    void request_whenImmediateDowngrade_thenReject() {
        Purchase proPurchase = Purchase.builder()
                .id(100L)
                .userId(10L)
                .packageId(2L)
                .packageCode("PRO")
                .packageName("Pro")
                .price(new BigDecimal("300.00"))
                .currency("TRY")
                .status(PurchaseStatus.ACTIVE)
                .purchaseType(PurchaseType.PAID)
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .paymentMethodId(55L)
                .paymentConversationId("paid-conv-pro")
                .billingSnapshot(billingSnapshot)
                .startsAt(LocalDateTime.now().minusDays(5))
                .expiresAt(LocalDateTime.now().plusDays(25))
                .build();
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(proPurchase));
        when(planPackageService.findPackage(2L)).thenReturn(pro);
        when(planPackageService.findActivePackage(1L)).thenReturn(starter);
        when(planChangeRepository.existsByUserIdAndStatus(anyLong(), any())).thenReturn(false);

        PlanChangeRequest request = new PlanChangeRequest();
        request.setToPackageId(1L);
        request.setTiming(PlanChangeTiming.IMMEDIATE);
        request.setWarningAck(true);

        assertThatThrownBy(() -> planChangeService.request(user, request, "1.1.1.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("donem sonunda");
        verify(paymentServiceClient, never()).initializeCheckoutForm(anyLong(), any());
        verify(paymentServiceClient, never()).refundPayment(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void request_whenDuplicateScheduled_thenReject() {
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(currentPurchase));
        when(planPackageService.findPackage(1L)).thenReturn(starter);
        when(planPackageService.findActivePackage(2L)).thenReturn(pro);
        when(planChangeRepository.existsByUserIdAndStatus(10L, PlanChangeStatus.SCHEDULED)).thenReturn(true);

        PlanChangeRequest request = new PlanChangeRequest();
        request.setToPackageId(2L);
        request.setTiming(PlanChangeTiming.NEXT_PERIOD);
        request.setPaymentMethodId(55L);
        request.setWarningAck(true);

        assertThatThrownBy(() -> planChangeService.request(user, request, "1.1.1.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("planlanmis");
    }

    @Test
    void cancelScheduled_whenScheduled_thenCancelled() {
        PlanChange scheduled = PlanChange.builder()
                .id(5L)
                .userId(10L)
                .fromPurchaseId(100L)
                .fromPackageId(1L)
                .toPackageId(2L)
                .direction(PlanChangeDirection.DOWNGRADE)
                .timing(PlanChangeTiming.NEXT_PERIOD)
                .status(PlanChangeStatus.SCHEDULED)
                .chargeAmount(new BigDecimal("100.00"))
                .currency("TRY")
                .warningAck(true)
                .effectiveAt(LocalDateTime.now().plusDays(10))
                .build();
        when(planChangeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(scheduled));
        when(planChangeRepository.save(any(PlanChange.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planPackageRepository.findById(1L)).thenReturn(Optional.of(starter));
        when(planPackageRepository.findById(2L)).thenReturn(Optional.of(pro));

        PlanChangeResponse response = planChangeService.cancelScheduled(10L, 5L);

        assertThat(response.getStatus()).isEqualTo(PlanChangeStatus.CANCELLED);
        ArgumentCaptor<PlanChange> captor = ArgumentCaptor.forClass(PlanChange.class);
        verify(planChangeRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PlanChangeStatus.CANCELLED);
    }

    @Test
    void onPurchasePaymentFailed_whenPendingPayment_thenFailed() {
        Purchase pending = Purchase.builder().id(200L).userId(10L).build();
        PlanChange pendingChange = PlanChange.builder()
                .id(8L)
                .userId(10L)
                .fromPurchaseId(100L)
                .fromPackageId(1L)
                .toPackageId(2L)
                .status(PlanChangeStatus.PENDING_PAYMENT)
                .resultingPurchaseId(200L)
                .build();
        when(planChangeRepository.findByResultingPurchaseIdForUpdate(200L)).thenReturn(Optional.of(pendingChange));
        when(planChangeRepository.save(any(PlanChange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planChangeService.onPurchasePaymentFailed(pending);

        assertThat(pendingChange.getStatus()).isEqualTo(PlanChangeStatus.FAILED);
        verify(planChangeRepository).save(pendingChange);
    }

    @Test
    void preview_whenNoPaidPackage_thenThrow() {
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE)).thenReturn(List.of());

        assertThatThrownBy(() -> planChangeService.preview(10L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ucretli paket");
    }
}
