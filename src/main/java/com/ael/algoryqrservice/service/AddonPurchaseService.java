package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AddonPurchaseRequest;
import com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.util.AppTime;
import com.ael.algoryqrservice.util.BillingPeriodResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddonPurchaseService {

    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseLogService purchaseLogService;
    private final BillingAddressService billingAddressService;
    private final PackagePricingService packagePricingService;
    private final PaymentRequestMapper paymentRequestMapper;
    private final PaymentServiceClient paymentServiceClient;
    private final PurchaseFulfillmentService purchaseFulfillmentService;
    private final AppProperties appProperties;
    private final PaymentClientProperties paymentClientProperties;
    private final EntitlementService entitlementService;

    @Transactional(noRollbackFor = PaymentServiceException.class)
    public PurchaseInitiateResponse purchase(User user, AddonPurchaseRequest request, String clientIp) {
        String productCode = request.resolvedProductCode();
        Product product = productRepository.findByCode(productCode)
                .filter(Product::isActive)
                .orElseThrow(() -> new BadRequestException("Urun bulunamadi veya aktif degil"));
        if (!product.isAddonPurchasable()) {
            throw new BadRequestException("Bu urun tekil olarak satin alinamaz");
        }
        if (!product.isConsumable()) {
            throw new BadRequestException("Bu urun adetli satin alinamaz");
        }

        entitlementService.expireDuePurchasesForUser(user.getId());
        Purchase host = findHostPurchase(user.getId());
        BillingPeriod billingPeriod = BillingPeriodResolver.resolve(host);
        PackagePricingService.LinePrice line = packagePricingService.calculateProduct(
                product,
                request.resolvedQuantity()
        );
        if (line.lineTotal().signum() <= 0) {
            throw new BadRequestException("Urun fiyati gecersiz");
        }

        LocalDateTime startsAt = AppTime.nowLocal();
        LocalDateTime expiresAt = host.getExpiresAt() != null && host.getExpiresAt().isAfter(startsAt)
                ? host.getExpiresAt()
                : startsAt.plusDays(30);

        BillingSnapshot billingSnapshot = request.getBillingAddress() != null
                ? billingAddressService.legacySnapshot(user.getId(), request.getBillingAddress(), request.getIdentityNumber())
                : billingAddressService.resolveSnapshot(
                        user.getId(), request.getBillingAddressId(), request.getInlineBillingAddress());
        String conversationId = paymentRequestMapper.newPaymentAttemptId(user.getId());

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .userId(user.getId())
                .packageId(host.getPackageId())
                .productId(product.getId())
                .packageCode(product.getCode())
                .packageName(product.getName())
                .addonQuantity(request.resolvedQuantity())
                .price(line.lineTotal())
                .currency("TRY")
                .paymentMode(PaymentMode.CHECKOUT_FORM)
                .paymentStyle(PaymentStyle.ONE_TIME)
                .purchaseType(PurchaseType.ADD_ON)
                .billingPeriod(billingPeriod)
                .billingIntervalMonths(billingPeriod.intervalMonths())
                .installmentCount(request.resolvedQuantity())
                .paymentMethodId(request.getPaymentMethodId())
                .billingSnapshot(billingSnapshot)
                .paymentConversationId(conversationId)
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .status(PurchaseStatus.PENDING)
                .build());

        purchaseFulfillmentService.initializeSchedule(purchase, appProperties.getServiceName());
        purchaseLogService.log(
                purchase.getId(),
                user.getId(),
                PurchaseLogAction.PURCHASE_STARTED,
                product.getName() + " urunu satin alma baslatildi"
        );

        try {
            PaymentCheckoutFormRequest checkoutFormRequest = paymentRequestMapper.toAddonCheckoutFormRequest(
                    purchase,
                    user,
                    product.getCode(),
                    product.getName(),
                    clientIp,
                    appProperties,
                    paymentClientProperties,
                    conversationId,
                    startsAt,
                    expiresAt
            );
            PaymentCheckoutFormResponse checkoutFormResponse =
                    paymentServiceClient.initializeCheckoutForm(user.getId(), checkoutFormRequest);
            if (checkoutFormResponse.getConversationId() != null
                    && !checkoutFormResponse.getConversationId().isBlank()) {
                purchase.setPaymentConversationId(checkoutFormResponse.getConversationId());
                purchaseRepository.save(purchase);
            }
            return PurchaseInitiateResponse.builder()
                    .purchaseId(purchase.getId())
                    .status(purchase.getStatus())
                    .conversationId(checkoutFormResponse.getConversationId())
                    .token(checkoutFormResponse.getToken())
                    .paymentPageUrl(checkoutFormResponse.getPaymentPageUrl())
                    .checkoutFormContent(checkoutFormResponse.getCheckoutFormContent())
                    .build();
        } catch (PaymentServiceException exception) {
            purchase.setStatus(PurchaseStatus.FAILED);
            purchase.setPaymentConversationId(null);
            purchaseRepository.save(purchase);
            purchaseLogService.log(
                    purchase.getId(),
                    user.getId(),
                    PurchaseLogAction.PURCHASE_PAYMENT_FAILED,
                    "Urun odemesi baslatilamadi: " + exception.getMessage()
            );
            throw exception;
        }
    }

    private Purchase findHostPurchase(Long userId) {
        return purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.ADD_ON)
                .filter(purchase -> purchase.getPackageId() != null)
                .max(Comparator.comparing(Purchase::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BadRequestException("Ek urun almak icin aktif bir paket gerekir"));
    }
}
