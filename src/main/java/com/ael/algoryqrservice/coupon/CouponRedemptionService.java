package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.coupon.domain.CouponPolicy;
import com.ael.algoryqrservice.coupon.domain.CouponQuote;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Coupon;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.CouponLogAction;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.repository.CouponRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CouponRedemptionService {

    public record AppliedCoupon(Coupon coupon, CouponQuote quote) {
    }

    private final CouponRepository couponRepository;
    private final CouponDiscountCalculator couponDiscountCalculator;
    private final CouponLogWriter couponLogWriter;

    @Transactional
    public AppliedCoupon reserve(String rawCode, Long userId, Long purchaseId, BigDecimal listPrice) {
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
                    userId,
                    purchaseId,
                    CouponLogAction.RESERVED,
                    quote,
                    "Kupon satin alim icin rezerve edildi"
            );
            return new AppliedCoupon(coupon, quote);
        } catch (BadRequestException exception) {
            couponLogWriter.write(
                    coupon.getId(),
                    userId,
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
        coupon.setUsedByUserId(purchase.getUserId());
        coupon.setUsedAt(AppTime.nowLocal());
        coupon.setReservedPurchaseId(null);
        couponRepository.save(coupon);
        couponLogWriter.write(
                coupon.getId(),
                purchase.getUserId(),
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
                purchase.getUserId(),
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
