package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class AmountCouponDiscount implements CouponDiscount {

    @Override
    public CouponDiscountType type() {
        return CouponDiscountType.AMOUNT;
    }

    @Override
    public BigDecimal discount(BigDecimal listPrice, BigDecimal value) {
        return value.min(listPrice).setScale(2, RoundingMode.HALF_UP);
    }
}
