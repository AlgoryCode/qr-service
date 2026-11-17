package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminMemberDtos.GrantRequest;
import com.ael.algoryqrservice.adminservice.dto.AdminMemberDtos.GrantResponse;
import com.ael.algoryqrservice.adminservice.dto.AdminMemberDtos.GrantableProduct;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.AdminGrant;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.AdminGrantRepository;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.messaging.fulfillment.FulfillmentQueuePublisher;
import com.ael.algoryqrservice.qrservice.model.PlanPackage;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.PurchaseItem;
import com.ael.algoryqrservice.qrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.ProductBillingType;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseCancellationReason;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseItemRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Free modules and credits given by an admin. Each grant is a zero-amount add-on purchase, so
 * access, expiry and revocation follow the same path as a bought add-on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminGrantService {

    static final String FALLBACK_PACKAGE = "FREE_PACKAGE";

    private final AdminGrantRepository grantRepository;
    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PlanPackageRepository planPackageRepository;
    private final UserRepository userRepository;
    private final FulfillmentQueuePublisher fulfillmentQueuePublisher;

    @Transactional(readOnly = true)
    public List<GrantableProduct> grantableProducts() {
        return productRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(p -> new GrantableProduct(p.getId(), p.getCode(), p.getName(), p.isConsumable(),
                        p.getBillingType() == null ? null : p.getBillingType().name(), p.getUnitPrice()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> list(Long merchantId) {
        LocalDateTime now = AppTime.nowLocal();
        return grantRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(g -> toResponse(g, now)).toList();
    }

    @Transactional
    public GrantResponse grant(Long merchantId, GrantRequest request, String actor) {
        if (!userRepository.existsById(merchantId)) {
            throw new NotFoundException("Üye bulunamadı");
        }
        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new BadRequestException("Ürün bulunamadı veya pasif"));
        boolean credit = isCredit(product);
        if (!credit && request.days() == null) {
            throw new BadRequestException("Modül için süre (gün) gerekli");
        }
        LocalDateTime now = AppTime.nowLocal();
        LocalDateTime expiresAt = request.days() == null ? null : now.plusDays(request.days());
        int quantity = request.quantity();

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .merchantId(merchantId)
                .packageId(hostPackageId(merchantId))
                .productId(product.getId())
                .packageCode(product.getCode())
                .packageName(product.getName() + " (yönetici hediyesi)")
                .addonQuantity(quantity)
                .price(BigDecimal.ZERO)
                .listPrice(BigDecimal.ZERO)
                .currency("TRY")
                .paymentMode(PaymentMode.DIRECT)
                .paymentStyle(PaymentStyle.ONE_TIME)
                .purchaseType(PurchaseType.ADD_ON)
                .billingPeriod(BillingPeriod.ONE_TIME)
                .billingIntervalMonths(0)
                .installmentCount(1)
                .systemManaged(true)
                .subscriptionStatusChangedBy(actor)
                .startsAt(now)
                .expiresAt(expiresAt)
                .purchasedAt(now)
                .status(PurchaseStatus.ACTIVE)
                .build());
        purchaseItemRepository.save(PurchaseItem.builder()
                .purchaseId(purchase.getId())
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .quantity(quantity)
                .unitPrice(BigDecimal.ZERO)
                .vatRate(BigDecimal.ZERO)
                .lineSubtotal(BigDecimal.ZERO)
                .lineVat(BigDecimal.ZERO)
                .lineTotal(BigDecimal.ZERO)
                .billingType(credit ? ProductBillingType.ONE_TIME : ProductBillingType.RECURRING)
                .createdAt(now)
                .build());

        AdminGrant grant = grantRepository.save(AdminGrant.builder()
                .merchantId(merchantId)
                .purchaseId(purchase.getId())
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .quantity(quantity)
                .days(request.days())
                .reason(request.reason().trim())
                .grantedBy(actor)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build());
        try {
            fulfillmentQueuePublisher.publishGranted(purchase);
        } catch (com.ael.algoryqrservice.fulfillmentservice.domain.exception.ResourceNotFoundException e) {
            throw new BadRequestException("Üyenin aktif bir paketi yok; önce paket tanımlayın");
        }
        log.info("admin_grant_created merchantId={} product={} quantity={} days={} by={}",
                merchantId, product.getCode(), quantity, request.days(), actor);
        return toResponse(grant, now);
    }

    @Transactional
    public GrantResponse revoke(Long merchantId, Long grantId, String actor) {
        AdminGrant grant = grantRepository.findById(grantId)
                .filter(g -> g.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new NotFoundException("Hediye bulunamadı"));
        LocalDateTime now = AppTime.nowLocal();
        if (grant.getRevokedAt() != null) {
            return toResponse(grant, now);
        }
        Purchase purchase = purchaseRepository.findById(grant.getPurchaseId())
                .orElseThrow(() -> new NotFoundException("Hediye kaydı bulunamadı"));
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setCancellationReason(PurchaseCancellationReason.MANUAL);
        purchase.setSubscriptionStatusChangedBy(actor);
        purchaseRepository.save(purchase);
        fulfillmentQueuePublisher.publishRevoked(purchase);
        grant.setRevokedAt(now);
        grant.setRevokedBy(actor);
        return toResponse(grantRepository.save(grant), now);
    }

    /** One-time consumables (report credits) stay until used; anything else is a timed module. */
    static boolean isCredit(Product product) {
        return product.isConsumable()
                && (product.getBillingType() == null || product.getBillingType() == ProductBillingType.ONE_TIME);
    }

    private Long hostPackageId(Long merchantId) {
        return purchaseRepository.findByMerchantIdAndStatus(merchantId, PurchaseStatus.ACTIVE).stream()
                .filter(p -> p.getPurchaseType() != PurchaseType.ADD_ON && p.getPackageId() != null)
                .max(Comparator.comparing(Purchase::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(Purchase::getPackageId)
                .or(() -> planPackageRepository.findByCode(FALLBACK_PACKAGE).map(PlanPackage::getId))
                .orElseThrow(() -> new BadRequestException("Üyenin bağlanacağı paket bulunamadı"));
    }

    private static GrantResponse toResponse(AdminGrant g, LocalDateTime now) {
        return new GrantResponse(g.getId(), g.getPurchaseId(), g.getProductId(), g.getProductCode(), g.getProductName(),
                g.getQuantity(), g.getDays(), g.getReason(), g.getGrantedBy(), g.getCreatedAt(), g.getExpiresAt(),
                g.getRevokedAt(), g.getRevokedBy(), g.isActive(now));
    }
}
