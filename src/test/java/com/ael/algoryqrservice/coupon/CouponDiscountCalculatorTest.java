package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.coupon.domain.CouponQuote;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponDiscountCalculatorTest {

    CouponDiscountCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CouponDiscountCalculator(List.of(new PercentCouponDiscount(), new AmountCouponDiscount()));
    }

    @Test
    void quote_whenPercent_thenReduceList() {
        CouponQuote quote = calculator.quote(CouponDiscountType.PERCENT, new BigDecimal("10"), new BigDecimal("200.00"));

        assertThat(quote.listPrice()).isEqualByComparingTo("200.00");
        assertThat(quote.discountAmount()).isEqualByComparingTo("20.00");
        assertThat(quote.payable()).isEqualByComparingTo("180.00");
    }

    @Test
    void quote_whenAmount_thenSubtract() {
        CouponQuote quote = calculator.quote(CouponDiscountType.AMOUNT, new BigDecimal("25.50"), new BigDecimal("100.00"));

        assertThat(quote.discountAmount()).isEqualByComparingTo("25.50");
        assertThat(quote.payable()).isEqualByComparingTo("74.50");
    }

    @Test
    void quote_whenDiscountCoversList_thenReject() {
        assertThatThrownBy(() -> calculator.quote(
                CouponDiscountType.AMOUNT,
                new BigDecimal("100.00"),
                new BigDecimal("100.00")
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("asiyor");
    }
}
