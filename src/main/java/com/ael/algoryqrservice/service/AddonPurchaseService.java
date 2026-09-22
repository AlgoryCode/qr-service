package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.PaymentServiceClient;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormResponse;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.PaymentServiceException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.PurchaseItem;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.AddonCartLineRequest;
import com.ael.algoryqrservice.model.dto.AddonCartPurchaseRequest;
import com.ael.algoryqrservice.model.dto.AddonPurchaseRequest;
import com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.PurchaseItemRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.service.entitlement.PurchaseExpiryService;
import com.ael.algoryqrservice.util.AppTime;
import com.ael.algoryqrservice.util.BillingPeriodResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddonPurchaseService {

    private static final int MAX_CART_LINES = 20;

    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchaseLogService purchaseLogService;
    private final BillingAddressService billingAddressService;
    private final PackagePricingService packagePricingService;
    private final PaymentRequestMapper paymentRequestMapper;
    private final PaymentServiceClient paymentServiceClient;
    private final PurchaseFulfillmentService purchaseFulfillmentService;
    private final AppProperties appProperties;
    private final PaymentClientProperties paymentClientProperties;
    private final PurchaseExpiryService purchaseExpiryService;

    @Transactional(noRollbackFor = PaymentServiceException.class)
    public PurchaseInitiateResponse purchase(User user, AddonPurchaseRequest request, String clientIp) {
        AddonCartLineRequest line = new AddonCartLineRequest(
                request.resolvedProductCode(),
                request.resolvedQuantity()
        );
        AddonCartPurchaseRequest cartRequest = new AddonCartPurchaseRequest();
        cartRequest.setModules(List.of(line));
        cartRequest.setPaymentMode(request.getPaymentMode());
        cartRequest.setBillingAddressId(request.getBillingAddressId());
        cartRequest.setPaymentMethodId(request.getPaymentMethodId());
        cartRequest.setInlineBillingAddress(request.getInlineBillingAddress());
        cartRequest.setBillingAddress(request.getBillingAddress());
        cartRequest.setIdentityNumber(request.getIdentityNumber());
        return purchaseCart(user, cartRequest, clientIp);
    }

    @Transactional(noRollbackFor = PaymentServiceException.class)
    public PurchaseInitiateResponse purchaseCart(User user, AddonCartPurchaseRequest request, String clientIp) {
        purchaseExpiryService.expireDueForUser(user.getId());
        Purchase host = findHostPurchase(user.getId());
        List<ResolvedLine> lines = resolveLines(request.getModules());
        BillingPeriod billingPeriod = BillingPeriodResolver.resolve(host);

        BigDecimal total = lines.stream()
                .map(ResolvedLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        if (total.signum() <= 0) {
            throw new BadRequestException("Sepet tutari gecersiz");
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

        ResolvedLine primary = lines.getFirst();
        String packageName = lines.size() == 1
                ? primary.product().getName()
                : "Modul sepeti (" + lines.size() + " urun)";

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .userId(user.getId())
                .packageId(host.getPackageId())
                .productId(primary.product().getId())
                .packageCode(primary.product().getCode())
                .packageName(packageName)
                .addonQuantity(primary.quantity())
                .price(total)
                .currency("TRY")
                .paymentMode(PaymentMode.CHECKOUT_FORM)
                .paymentStyle(PaymentStyle.ONE_TIME)
                .purchaseType(PurchaseType.ADD_ON)
                .billingPeriod(billingPeriod)
                .billingIntervalMonths(billingPeriod.intervalMonths())
                .installmentCount(primary.quantity())
                .paymentMethodId(request.getPaymentMethodId())
                .billingSnapshot(billingSnapshot)
                .paymentConversationId(conversationId)
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .status(PurchaseStatus.PENDING)
                .build());

        for (ResolvedLine line : lines) {
            ProductBillingType billingType = line.product().getBillingType() == null
                    ? ProductBillingType.ONE_TIME
                    : line.product().getBillingType();
            purchaseItemRepository.save(PurchaseItem.builder()
                    .purchaseId(purchase.getId())
                    .productId(line.product().getId())
                    .productCode(line.product().getCode())
                    .productName(line.product().getName())
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .vatRate(line.vatRate())
                    .lineSubtotal(line.lineSubtotal())
                    .lineVat(line.lineVat())
                    .lineTotal(line.lineTotal())
                    .billingType(billingType)
                    .unlimited(false)
                    .build());
        }

        purchaseFulfillmentService.initializeSchedule(purchase, appProperties.getServiceName());
        purchaseLogService.log(
                purchase.getId(),
                user.getId(),
                PurchaseLogAction.PURCHASE_STARTED,
                packageName + " satin alma baslatildi"
        );

        List<PaymentThreeDsRequest.BasketItemPayload> basketItems = lines.stream()
                .map(line -> PaymentThreeDsRequest.BasketItemPayload.builder()
                        .id(line.product().getCode())
                        .name(line.product().getName())
                        .category1("Digital")
                        .category2("Product")
                        .itemType("VIRTUAL")
                        .price(line.lineTotal())
                        .build())
                .toList();

        try {
            PaymentCheckoutFormRequest checkoutFormRequest = paymentRequestMapper.toAddonCartCheckoutFormRequest(
                    purchase,
                    user,
                    basketItems,
                    primary.product().getCode(),
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
                    "Modul sepeti odemesi baslatilamadi: " + exception.getMessage()
            );
            throw exception;
        }
    }

    private List<ResolvedLine> resolveLines(List<AddonCartLineRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new BadRequestException("Sepette en az bir modul olmalidir");
        }
        if (requested.size() > MAX_CART_LINES) {
            throw new BadRequestException("Sepette en fazla " + MAX_CART_LINES + " modul olabilir");
        }

        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (AddonCartLineRequest line : requested) {
            String code = line.resolvedProductCode();
            if (code.isBlank()) {
                throw new BadRequestException("Urun kodu zorunludur");
            }
            quantities.merge(code, line.resolvedQuantity(), Integer::sum);
        }

        List<ResolvedLine> resolved = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            Product product = productRepository.findByCode(entry.getKey())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new BadRequestException("Urun bulunamadi veya aktif degil: " + entry.getKey()));
            if (!product.isAddonPurchasable()) {
                throw new BadRequestException("Bu urun tekil olarak satin alinamaz: " + product.getCode());
            }
            int quantity = entry.getValue();
            if (!product.isConsumable() && quantity > 1) {
                throw new BadRequestException("Bu urun adetli satin alinamaz: " + product.getCode());
            }
            PackagePricingService.LinePrice priced = packagePricingService.calculateProduct(product, quantity);
            if (priced.lineTotal().signum() <= 0) {
                throw new BadRequestException("Urun fiyati gecersiz: " + product.getCode());
            }
            resolved.add(new ResolvedLine(
                    product,
                    quantity,
                    priced.unitPrice(),
                    priced.vatRate(),
                    priced.lineSubtotal(),
                    priced.lineVat(),
                    priced.lineTotal()
            ));
        }
        return resolved;
    }

    private Purchase findHostPurchase(Long userId) {
        return purchaseRepository.findByUserIdAndStatus(userId, PurchaseStatus.ACTIVE).stream()
                .filter(Purchase::isUsable)
                .filter(purchase -> purchase.getPurchaseType() != PurchaseType.ADD_ON)
                .filter(purchase -> purchase.getPackageId() != null)
                .max(Comparator.comparing(Purchase::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BadRequestException("Ek urun almak icin aktif bir paket gerekir"));
    }

    private record ResolvedLine(
            Product product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            BigDecimal lineSubtotal,
            BigDecimal lineVat,
            BigDecimal lineTotal
    ) {
    }
}
