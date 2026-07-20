package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.BillingPaymentDtos;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsResponse;
import com.ael.algoryqrservice.config.AppProperties;
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
        assertThat(preview.getOptions().getFirst().getChargeNow()).isEqualByComparingTo("200.00");
        assertThat(preview.getOptions().getFirst().getRefundNow()).isEqualByComparingTo("0");
        assertThat(preview.getOptions().get(1).getTiming()).isEqualTo(PlanChangeTiming.NEXT_PERIOD);
        assertThat(preview.getOptions().get(1).getChargeNow()).isEqualByComparingTo("0");
        assertThat(preview.getOptions().get(1).getChargeAtEffective()).isEqualByComparingTo("300.00");
        assertThat(preview.getWarnings()).isNotEmpty();
    }

    @Test
    void preview_whenDowngrade_thenImmediateRefundDifference() {
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
        when(paymentServiceClient.getRefundablePayment(10L, "paid-conv-pro"))
                .thenReturn(new BillingPaymentDtos.RefundablePayment(
                        "paid-conv-pro",
                        "pay-1",
                        "tx-1",
                        "SUCCESS",
                        new BigDecimal("300.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("300.00")
                ));

        PlanChangePreviewResponse preview = planChangeService.preview(10L, 1L);

        assertThat(preview.getDirection()).isEqualTo(PlanChangeDirection.DOWNGRADE);
        assertThat(preview.getOptions().getFirst().getChargeNow()).isEqualByComparingTo("0");
        assertThat(preview.getOptions().getFirst().getRefundNow()).isEqualByComparingTo("200.00");
    }

    @Test
    void preview_whenDowngradeAndRemainingLower_thenClampsRefundNow() {
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
                        .paymentConversationId("paid-conv-diff")
                        .expiresAt(LocalDateTime.now().plusDays(20))
                        .build()));
        when(planPackageService.findPackage(2L)).thenReturn(pro);
        when(planPackageService.findActivePackage(1L)).thenReturn(starter);
        when(planChangeRepository.existsByUserIdAndStatus(10L, PlanChangeStatus.SCHEDULED)).thenReturn(false);
        when(paymentServiceClient.getRefundablePayment(10L, "paid-conv-diff"))
                .thenReturn(new BillingPaymentDtos.RefundablePayment(
                        "paid-conv-diff",
                        "pay-2",
                        "tx-2",
                        "SUCCESS",
                        new BigDecimal("150.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("150.00")
                ));

        PlanChangePreviewResponse preview = planChangeService.preview(10L, 1L);

        assertThat(preview.getOptions().getFirst().getRefundNow()).isEqualByComparingTo("150.00");
        assertThat(preview.getWarnings()).anyMatch(w -> w.contains("kalan bakiyeyle"));
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
        verify(paymentServiceClient, never()).createDirectPayment(any());
    }

    @Test
    void request_whenImmediate_thenChargesAndCompletes() {
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(currentPurchase));
        when(planPackageService.findPackage(1L)).thenReturn(starter);
        when(planPackageService.findActivePackage(2L)).thenReturn(pro);
        when(planChangeRepository.existsByUserIdAndStatus(anyLong(), any())).thenReturn(false);
        when(paymentServiceClient.getPaymentMethods(10L)).thenReturn(List.of(
                new BillingPaymentDtos.PaymentMethod("55", "Kart", "visa", "4242", 12, 2030)
        ));
        when(appProperties.getServiceName()).thenReturn("qr-service");
        when(paymentRequestMapper.buildConversationId(anyLong())).thenReturn("conv-1");
        when(paymentServiceClient.createDirectPayment(any())).thenReturn(new PaymentThreeDsResponse());
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
        when(planChangeRepository.findById(11L)).thenAnswer(invocation -> {
            PlanChange pc = new PlanChange();
            pc.setId(11L);
            pc.setUserId(10L);
            pc.setFromPurchaseId(100L);
            pc.setFromPackageId(1L);
            pc.setToPackageId(2L);
            pc.setDirection(PlanChangeDirection.UPGRADE);
            pc.setTiming(PlanChangeTiming.IMMEDIATE);
            pc.setStatus(PlanChangeStatus.COMPLETED);
            pc.setChargeAmount(new BigDecimal("200.00"));
            pc.setRefundAmount(BigDecimal.ZERO);
            pc.setCurrency("TRY");
            pc.setWarningAck(true);
            pc.setResultingPurchaseId(200L);
            return Optional.of(pc);
        });
        when(planPackageRepository.findById(1L)).thenReturn(Optional.of(starter));
        when(planPackageRepository.findById(2L)).thenReturn(Optional.of(pro));

        PlanChangeRequest request = new PlanChangeRequest();
        request.setToPackageId(2L);
        request.setTiming(PlanChangeTiming.IMMEDIATE);
        request.setPaymentMethodId(55L);
        request.setWarningAck(true);

        PlanChangeResponse response = planChangeService.request(user, request, "1.1.1.1");

        assertThat(response.getStatus()).isEqualTo(PlanChangeStatus.COMPLETED);
        ArgumentCaptor<PaymentThreeDsRequest> paymentCaptor = ArgumentCaptor.forClass(PaymentThreeDsRequest.class);
        verify(paymentServiceClient).createDirectPayment(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPrice()).isEqualByComparingTo("200.00");
        verify(paymentServiceClient, never()).refundPayment(anyLong(), anyString(), any(), anyString());
        verify(entitlementService).grant(any(), eq(7L), eq("QR_CREATE"), eq(50), eq(false));
        verify(packageActivationService).activatePurchasedPackage(any());
        verify(entitlementService).revokeForCancelledPurchase(currentPurchase);
    }

    @Test
    void request_whenImmediateDowngrade_thenRefundsDifferenceWithoutCharge() {
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
        when(paymentRequestMapper.buildConversationId(anyLong())).thenReturn("conv-down");
        when(paymentServiceClient.getRefundablePayment(10L, "paid-conv-pro"))
                .thenReturn(new BillingPaymentDtos.RefundablePayment(
                        "paid-conv-pro",
                        "pay-1",
                        "tx-1",
                        "SUCCESS",
                        new BigDecimal("300.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("300.00")
                ));
        when(paymentServiceClient.refundPayment(eq(10L), eq("paid-conv-pro"), any(), eq("1.1.1.1")))
                .thenReturn(new BillingPaymentDtos.RefundResult(
                        "paid-conv-pro", "tx-1", new BigDecimal("200.00"), "SUCCESS"));
        when(planChangeRepository.save(any(PlanChange.class))).thenAnswer(invocation -> {
            PlanChange pc = invocation.getArgument(0);
            if (pc.getId() == null) {
                pc.setId(12L);
            }
            return pc;
        });
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(201L);
            }
            return p;
        });
        when(planChangeRepository.findById(12L)).thenAnswer(invocation -> {
            PlanChange pc = new PlanChange();
            pc.setId(12L);
            pc.setUserId(10L);
            pc.setFromPurchaseId(100L);
            pc.setFromPackageId(2L);
            pc.setToPackageId(1L);
            pc.setDirection(PlanChangeDirection.DOWNGRADE);
            pc.setTiming(PlanChangeTiming.IMMEDIATE);
            pc.setStatus(PlanChangeStatus.COMPLETED);
            pc.setChargeAmount(BigDecimal.ZERO);
            pc.setRefundAmount(new BigDecimal("200.00"));
            pc.setCurrency("TRY");
            pc.setWarningAck(true);
            pc.setResultingPurchaseId(201L);
            return Optional.of(pc);
        });
        when(planPackageRepository.findById(1L)).thenReturn(Optional.of(starter));
        when(planPackageRepository.findById(2L)).thenReturn(Optional.of(pro));

        PlanChangeRequest request = new PlanChangeRequest();
        request.setToPackageId(1L);
        request.setTiming(PlanChangeTiming.IMMEDIATE);
        request.setWarningAck(true);

        PlanChangeResponse response = planChangeService.request(user, request, "1.1.1.1");

        assertThat(response.getStatus()).isEqualTo(PlanChangeStatus.COMPLETED);
        assertThat(response.getRefundAmount()).isEqualByComparingTo("200.00");
        verify(paymentServiceClient).refundPayment(
                eq(10L), eq("paid-conv-pro"), eq(new BigDecimal("200.00")), eq("1.1.1.1"));
        verify(paymentServiceClient, never()).createDirectPayment(any());
        verify(packageActivationService).activatePurchasedPackage(any());
        verify(entitlementService).revokeForCancelledPurchase(proPurchase);
    }

    @Test
    void request_whenImmediateDowngradeAndNoRemaining_thenSwitchesWithoutRefund() {
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
                .paymentConversationId("paid-conv-empty")
                .billingSnapshot(billingSnapshot)
                .startsAt(LocalDateTime.now().minusDays(5))
                .expiresAt(LocalDateTime.now().plusDays(25))
                .build();
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE))
                .thenReturn(List.of(proPurchase));
        when(planPackageService.findPackage(2L)).thenReturn(pro);
        when(planPackageService.findActivePackage(1L)).thenReturn(starter);
        when(planChangeRepository.existsByUserIdAndStatus(anyLong(), any())).thenReturn(false);
        when(paymentRequestMapper.buildConversationId(anyLong())).thenReturn("conv-down-0");
        when(paymentServiceClient.getRefundablePayment(10L, "paid-conv-empty"))
                .thenReturn(new BillingPaymentDtos.RefundablePayment(
                        "paid-conv-empty",
                        "pay-0",
                        "tx-0",
                        "SUCCESS",
                        new BigDecimal("200.00"),
                        new BigDecimal("200.00"),
                        BigDecimal.ZERO
                ));
        when(planChangeRepository.save(any(PlanChange.class))).thenAnswer(invocation -> {
            PlanChange pc = invocation.getArgument(0);
            if (pc.getId() == null) {
                pc.setId(13L);
            }
            return pc;
        });
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(202L);
            }
            return p;
        });
        when(planChangeRepository.findById(13L)).thenAnswer(invocation -> {
            PlanChange pc = new PlanChange();
            pc.setId(13L);
            pc.setUserId(10L);
            pc.setFromPurchaseId(100L);
            pc.setFromPackageId(2L);
            pc.setToPackageId(1L);
            pc.setDirection(PlanChangeDirection.DOWNGRADE);
            pc.setTiming(PlanChangeTiming.IMMEDIATE);
            pc.setStatus(PlanChangeStatus.COMPLETED);
            pc.setChargeAmount(BigDecimal.ZERO);
            pc.setRefundAmount(BigDecimal.ZERO);
            pc.setCurrency("TRY");
            pc.setWarningAck(true);
            pc.setResultingPurchaseId(202L);
            return Optional.of(pc);
        });
        when(planPackageRepository.findById(1L)).thenReturn(Optional.of(starter));
        when(planPackageRepository.findById(2L)).thenReturn(Optional.of(pro));

        PlanChangeRequest request = new PlanChangeRequest();
        request.setToPackageId(1L);
        request.setTiming(PlanChangeTiming.IMMEDIATE);
        request.setWarningAck(true);

        PlanChangeResponse response = planChangeService.request(user, request, "1.1.1.1");

        assertThat(response.getStatus()).isEqualTo(PlanChangeStatus.COMPLETED);
        assertThat(response.getRefundAmount()).isEqualByComparingTo("0");
        verify(paymentServiceClient, never()).refundPayment(anyLong(), anyString(), any(), anyString());
        verify(packageActivationService).activatePurchasedPackage(any());
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
    void preview_whenNoPaidPackage_thenThrow() {
        when(purchaseRepository.findByUserIdAndStatus(10L, PurchaseStatus.ACTIVE)).thenReturn(List.of());

        assertThatThrownBy(() -> planChangeService.preview(10L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ucretli paket");
    }
}
