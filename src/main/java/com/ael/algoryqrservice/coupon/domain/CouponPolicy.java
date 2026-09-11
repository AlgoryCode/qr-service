package com.ael.algoryqrservice.coupon.domain;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Coupon;
import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.util.AppTime;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

public final class CouponPolicy {

    private static final int MIN_CODE_LENGTH = 4;
    private static final int MAX_CODE_LENGTH = 32;
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    private CouponPolicy() {
    }

    public static String normalizeCode(String raw) {
        if (raw == null) {
            throw new BadRequestException("Kupon kodu zorunludur");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (code.length() < MIN_CODE_LENGTH || code.length() > MAX_CODE_LENGTH) {
            throw new BadRequestException("Kupon kodu 4-32 karakter olmalidir");
        }
        if (!code.chars().allMatch(ch -> Character.isLetterOrDigit(ch))) {
            throw new BadRequestException("Kupon kodu yalnizca harf ve rakam icermelidir");
        }
        return code;
    }

    public static void validateCreate(
            CouponDiscountType discountType,
            BigDecimal discountValue,
            LocalDateTime expiresAt
    ) {
        if (discountType == null) {
            throw new BadRequestException("Indirim tipi zorunludur");
        }
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Indirim degeri 0'dan buyuk olmalidir");
        }
        if (discountType == CouponDiscountType.PERCENT && discountValue.compareTo(MAX_PERCENT) > 0) {
            throw new BadRequestException("Yuzde indirim en fazla 100 olabilir");
        }
        if (expiresAt == null) {
            throw new BadRequestException("Kupon gecerlilik tarihi zorunludur");
        }
        if (!expiresAt.isAfter(AppTime.nowLocal())) {
            throw new BadRequestException("Kupon gecerlilik tarihi gelecekte olmalidir");
        }
    }

    public static void requireRedeemable(Coupon coupon) {
        LocalDateTime now = AppTime.nowLocal();
        if (coupon.getStatus() == CouponStatus.USED) {
            throw new BadRequestException("Kupon daha once kullanilmis");
        }
        if (coupon.getStatus() == CouponStatus.RESERVED) {
            throw new BadRequestException("Kupon baska bir satin alim icin rezerve");
        }
        if (coupon.getStatus() == CouponStatus.REVOKED) {
            throw new BadRequestException("Kupon iptal edilmis");
        }
        if (coupon.getStatus() != CouponStatus.UNUSED) {
            throw new BadRequestException("Kupon kullanilamaz");
        }
        LocalDateTime validFrom = coupon.getValidFrom() == null ? coupon.getCreatedAt() : coupon.getValidFrom();
        if (validFrom != null && validFrom.isAfter(now)) {
            throw new BadRequestException("Kupon henuz gecerli degil");
        }
        if (!coupon.getExpiresAt().isAfter(now)) {
            throw new BadRequestException("Kupon suresi dolmus");
        }
    }

    public static boolean previewUsable(Coupon coupon) {
        try {
            requireRedeemable(coupon);
            return true;
        } catch (BadRequestException ignored) {
            return false;
        }
    }
}
