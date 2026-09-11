package com.ael.algoryqrservice.coupon.domain;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Coupon;
import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponPolicyTest {

    @Test
    void normalizeCode_whenValid_thenUppercase() {
        assertThat(CouponPolicy.normalizeCode("  save10 ")).isEqualTo("SAVE10");
    }

    @Test
    void normalizeCode_whenShort_thenReject() {
        assertThatThrownBy(() -> CouponPolicy.normalizeCode("AB"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("4-32");
    }

    @Test
    void validateCreate_whenPastExpiry_thenReject() {
        assertThatThrownBy(() -> CouponPolicy.validateCreate(
                CouponDiscountType.AMOUNT,
                new BigDecimal("10"),
                AppTime.nowLocal().minusMinutes(1)
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("gelecekte");
    }

    @Test
    void validateCreate_whenPercentOver100_thenReject() {
        assertThatThrownBy(() -> CouponPolicy.validateCreate(
                CouponDiscountType.PERCENT,
                new BigDecimal("101"),
                AppTime.nowLocal().plusDays(1)
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100");
    }

    @Test
    void requireRedeemable_whenUsed_thenReject() {
        Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .status(CouponStatus.USED)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        assertThatThrownBy(() -> CouponPolicy.requireRedeemable(coupon))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("kullanilmis");
    }

    @Test
    void previewUsable_whenUnusedAndValid_thenTrue() {
        Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .status(CouponStatus.UNUSED)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        assertThat(CouponPolicy.previewUsable(coupon)).isTrue();
    }
}
