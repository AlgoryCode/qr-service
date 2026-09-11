package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.coupon.domain.CouponQuote;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Coupon;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import com.ael.algoryqrservice.model.enums.CouponLogAction;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponRedemptionServiceTest {

    @Mock
    CouponRepository couponRepository;
    @Mock
    CouponLogWriter couponLogWriter;

    CouponRedemptionService service;

    @BeforeEach
    void setUp() {
        CouponDiscountCalculator calculator = new CouponDiscountCalculator(
                List.of(new PercentCouponDiscount(), new AmountCouponDiscount())
        );
        service = new CouponRedemptionService(couponRepository, calculator, couponLogWriter);
    }

    @Test
    void reserve_whenUnused_thenReserved() {
        Coupon coupon = unused();
        when(couponRepository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));
        when(couponRepository.save(coupon)).thenReturn(coupon);

        CouponRedemptionService.AppliedCoupon applied = service.reserve(
                "save10", 7L, 99L, new BigDecimal("200.00"));

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESERVED);
        assertThat(coupon.getReservedPurchaseId()).isEqualTo(99L);
        assertThat(applied.quote().payable()).isEqualByComparingTo("180.00");
        verify(couponLogWriter).write(eq(1L), eq(7L), eq(99L), eq(CouponLogAction.RESERVED), any(), any());
    }

    @Test
    void reserve_whenAlreadyUsed_thenReject() {
        Coupon coupon = unused();
        coupon.setStatus(CouponStatus.USED);
        when(couponRepository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.reserve("SAVE10", 7L, 99L, new BigDecimal("200.00")))
                .isInstanceOf(BadRequestException.class);
        verify(couponRepository, never()).save(coupon);
        verify(couponLogWriter).write(eq(1L), eq(7L), eq(99L), eq(CouponLogAction.REJECTED), eq(null), any());
    }

    @Test
    void consume_whenReservedForPurchase_thenUsed() {
        Coupon coupon = unused();
        coupon.setStatus(CouponStatus.RESERVED);
        coupon.setReservedPurchaseId(99L);
        when(couponRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(coupon));
        Purchase purchase = Purchase.builder()
                .id(99L)
                .userId(7L)
                .couponId(1L)
                .listPrice(new BigDecimal("200.00"))
                .discountAmount(new BigDecimal("20.00"))
                .price(new BigDecimal("180.00"))
                .build();

        service.consume(purchase);

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(coupon.getUsedPurchaseId()).isEqualTo(99L);
        assertThat(coupon.getReservedPurchaseId()).isNull();
        verify(couponLogWriter).write(eq(1L), eq(7L), eq(99L), eq(CouponLogAction.USED), any(CouponQuote.class), any());
    }

    @Test
    void release_whenReserved_thenUnused() {
        Coupon coupon = unused();
        coupon.setStatus(CouponStatus.RESERVED);
        coupon.setReservedPurchaseId(99L);
        when(couponRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(coupon));
        Purchase purchase = Purchase.builder().id(99L).userId(7L).couponId(1L).build();

        service.release(purchase);

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.UNUSED);
        assertThat(coupon.getReservedPurchaseId()).isNull();
        verify(couponLogWriter).write(eq(1L), eq(7L), eq(99L), eq(CouponLogAction.RELEASED), eq(null), any());
    }

    private static Coupon unused() {
        return Coupon.builder()
                .id(1L)
                .code("SAVE10")
                .discountType(CouponDiscountType.PERCENT)
                .discountValue(new BigDecimal("10"))
                .status(CouponStatus.UNUSED)
                .expiresAt(LocalDateTime.now().plusDays(5))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }
}
