package com.ael.algoryqrservice.qrservice.coupon.domain;

import java.math.BigDecimal;

public record CouponQuote(
        BigDecimal listPrice,
        BigDecimal discountAmount,
        BigDecimal payable
) {
}
