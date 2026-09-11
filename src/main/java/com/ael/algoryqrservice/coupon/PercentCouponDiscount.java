package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PercentCouponDiscount implements CouponDiscount {

    @Override
    public CouponDiscountType type() {
        return CouponDiscountType.PERCENT;
    }

    @Override
    public BigDecimal discount(BigDecimal listPrice, BigDecimal value) {
        return listPrice.multiply(value)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
