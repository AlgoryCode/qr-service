package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Coupon;
import com.ael.algoryqrservice.model.dto.CouponDtos;
import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.repository.CouponLogRepository;
import com.ael.algoryqrservice.repository.CouponRepository;
import com.ael.algoryqrservice.util.AppTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
class CouponUseCasesTest {

    @Mock
    CouponRepository couponRepository;
    @Mock
    CouponLogRepository couponLogRepository;
    @Mock
    CouponLogWriter couponLogWriter;

    CouponUseCases useCases;

    @BeforeEach
    void setUp() {
        useCases = new CouponUseCases(couponRepository, couponLogRepository, couponLogWriter);
    }

    @Test
    void create_whenValid_thenUnused() {
        CouponDtos.CreateRequest request = new CouponDtos.CreateRequest(
                "save10",
                CouponDiscountType.PERCENT,
                new BigDecimal("10"),
                AppTime.nowLocal().plusDays(10),
                null
        );
        when(couponRepository.existsByCode("SAVE10")).thenReturn(false);
        when(couponRepository.save(any())).thenAnswer(invocation -> {
            Coupon coupon = invocation.getArgument(0);
            coupon.setId(5L);
            return coupon;
        });

        CouponDtos.Response response = useCases.create(request, 3L);

        assertThat(response.getCode()).isEqualTo("SAVE10");
        assertThat(response.getStatus()).isEqualTo(CouponStatus.UNUSED);
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedByAdminId()).isEqualTo(3L);
    }

    @Test
    void revoke_whenUsed_thenReject() {
        Coupon coupon = Coupon.builder().id(5L).status(CouponStatus.USED).build();
        when(couponRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> useCases.revoke(5L, "REVOKED"))
                .isInstanceOf(BadRequestException.class);
        verify(couponRepository, never()).save(coupon);
    }

    @Test
    void preview_whenUnused_thenUsable() {
        Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(CouponDiscountType.AMOUNT)
                .discountValue(new BigDecimal("20"))
                .status(CouponStatus.UNUSED)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        CouponDtos.PreviewResponse preview = useCases.preview("save10");

        assertThat(preview.isUsable()).isTrue();
        assertThat(preview.getDiscountType()).isEqualTo(CouponDiscountType.AMOUNT);
    }

    @Test
    void list_whenPaged_thenMapContent() {
        Coupon coupon = Coupon.builder()
                .id(1L)
                .code("SAVE10")
                .discountType(CouponDiscountType.PERCENT)
                .discountValue(new BigDecimal("10"))
                .status(CouponStatus.UNUSED)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        when(couponRepository.search(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(coupon)));

        CouponDtos.PageResponse page = useCases.list(null, null, 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getCode()).isEqualTo("SAVE10");
    }
}
