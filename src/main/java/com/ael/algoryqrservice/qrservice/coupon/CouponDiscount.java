package com.ael.algoryqrservice.qrservice.coupon;

import com.ael.algoryqrservice.qrservice.model.enums.CouponDiscountType;

import java.math.BigDecimal;

public interface CouponDiscount {

    CouponDiscountType type();

    BigDecimal discount(BigDecimal listPrice, BigDecimal value);
}
