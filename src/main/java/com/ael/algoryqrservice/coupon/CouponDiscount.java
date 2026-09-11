package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.model.enums.CouponDiscountType;

import java.math.BigDecimal;

public interface CouponDiscount {

    CouponDiscountType type();

    BigDecimal discount(BigDecimal listPrice, BigDecimal value);
}
