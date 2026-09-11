package com.ael.algoryqrservice.coupon.domain;

import java.math.BigDecimal;

public record CouponQuote(
        BigDecimal listPrice,
        BigDecimal discountAmount,
        BigDecimal payable
) {
}
