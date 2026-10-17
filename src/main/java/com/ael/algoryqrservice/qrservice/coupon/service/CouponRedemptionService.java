package com.ael.algoryqrservice.qrservice.coupon.service;

import com.ael.algoryqrservice.qrservice.coupon.domain.CouponPolicy;
import com.ael.algoryqrservice.qrservice.coupon.domain.CouponQuote;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Coupon;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.CouponLogAction;
import com.ael.algoryqrservice.qrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.qrservice.repository.CouponRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.ael.algoryqrservice.qrservice.coupon.CouponDiscountCalculator;
import com.ael.algoryqrservice.qrservice.coupon.CouponLogWriter;

@Service
@RequiredArgsConstructor
public class CouponRedemptionService {

    public record AppliedCoupon(Coupon coupon, CouponQuote quote) {
    }

    private final CouponRepository couponRepository;
    private final CouponDiscountCalculator couponDiscountCalculator;
    private final CouponLogWriter couponLogWriter;

    @Transactional
    public AppliedCoupon reserve(String rawCode, Long merchantId, Long purchaseId, BigDecimal listPrice) {
        String code = CouponPolicy.normalizeCode(rawCode);
        Coupon coupon = couponRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new BadRequestException("Kupon bulunamadi"));
        try {
            CouponPolicy.requireRedeemable(coupon);
            CouponQuote quote = couponDiscountCalculator.quote(
                    coupon.getDiscountType(),
                    coupon.getDiscountValue(),
                    listPrice
            );
            coupon.setStatus(CouponStatus.RESERVED);
            coupon.setReservedPurchaseId(purchaseId);
            couponRepository.save(coupon);
            couponLogWriter.write(
                    coupon.getId(),
                    merchantId,
                    purchaseId,
                    CouponLogAction.RESERVED,
                    quote,
                    "Kupon satin alim icin rezerve edildi"
            );
            return new AppliedCoupon(coupon, quote);
        } catch (BadRequestException exception) {
            couponLogWriter.write(
                    coupon.getId(),
                    merchantId,
                    purchaseId,
                    CouponLogAction.REJECTED,
                    null,
                    exception.getMessage()
            );
            throw exception;
        }
    }

    @Transactional
    public Coupon consume(Purchase purchase) {
        if (purchase.getCouponId() == null) {
            return null;
        }
        Coupon coupon = couponRepository.findByIdForUpdate(purchase.getCouponId()).orElse(null);
        if (coupon == null || coupon.getStatus() != CouponStatus.RESERVED) {
            return coupon;
        }
        if (!purchase.getId().equals(coupon.getReservedPurchaseId())) {
            return coupon;
        }
        coupon.setStatus(CouponStatus.USED);
        coupon.setUsedPurchaseId(purchase.getId());
        coupon.setUsedByMerchantId(purchase.getMerchantId());
        coupon.setUsedAt(AppTime.nowLocal());
        coupon.setReservedPurchaseId(null);
        couponRepository.save(coupon);
        couponLogWriter.write(
                coupon.getId(),
                purchase.getMerchantId(),
                purchase.getId(),
                CouponLogAction.USED,
                quoteOf(purchase),
                "Kupon kullanildi"
        );
        return coupon;
    }

    @Transactional
    public void release(Purchase purchase) {
        if (purchase.getCouponId() == null) {
            return;
        }
        Coupon coupon = couponRepository.findByIdForUpdate(purchase.getCouponId()).orElse(null);
        if (coupon == null || coupon.getStatus() != CouponStatus.RESERVED) {
            return;
        }
        if (!purchase.getId().equals(coupon.getReservedPurchaseId())) {
            return;
        }
        coupon.setStatus(CouponStatus.UNUSED);
        coupon.setReservedPurchaseId(null);
        couponRepository.save(coupon);
        couponLogWriter.write(
                coupon.getId(),
                purchase.getMerchantId(),
                purchase.getId(),
                CouponLogAction.RELEASED,
                quoteOf(purchase),
                "Kupon rezervasyonu geri alindi"
        );
    }

    private CouponQuote quoteOf(Purchase purchase) {
        if (purchase.getListPrice() == null || purchase.getDiscountAmount() == null) {
            return null;
        }
        return new CouponQuote(purchase.getListPrice(), purchase.getDiscountAmount(), purchase.getPrice());
    }
}
