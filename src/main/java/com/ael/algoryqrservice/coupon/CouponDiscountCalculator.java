package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.coupon.domain.CouponQuote;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CouponDiscountCalculator {

    private static final BigDecimal MIN_PAYABLE = new BigDecimal("0.01");

    private final Map<CouponDiscountType, CouponDiscount> discounts;

    public CouponDiscountCalculator(List<CouponDiscount> found) {
        this.discounts = found.stream()
                .collect(Collectors.toMap(CouponDiscount::type, Function.identity()));
    }

    public CouponQuote quote(CouponDiscountType type, BigDecimal value, BigDecimal listPrice) {
        CouponDiscount discount = discounts.get(type);
        if (discount == null) {
            throw new BadRequestException("Indirim tipi desteklenmiyor");
        }
        BigDecimal list = listPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal off = discount.discount(list, value);
        if (off.compareTo(list) >= 0) {
            throw new BadRequestException("Kupon indirimi paket fiyatini asiyor");
        }
        BigDecimal payable = list.subtract(off);
        if (payable.compareTo(MIN_PAYABLE) < 0) {
            throw new BadRequestException("Kupon indirimi paket fiyatini asiyor");
        }
        return new CouponQuote(list, off, payable);
    }
}
