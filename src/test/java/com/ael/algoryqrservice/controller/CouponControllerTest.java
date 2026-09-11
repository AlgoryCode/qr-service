package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.coupon.CouponUseCases;
import com.ael.algoryqrservice.model.dto.CouponDtos;
import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponControllerTest {

    @Test
    void preview_whenCode_thenDelegate() {
        CouponUseCases useCases = mock(CouponUseCases.class);
        CouponDtos.PreviewResponse preview = CouponDtos.PreviewResponse.builder()
                .code("SAVE10")
                .discountType(CouponDiscountType.PERCENT)
                .discountValue(new BigDecimal("10"))
                .expiresAt(LocalDateTime.now().plusDays(3))
                .usable(true)
                .build();
        when(useCases.preview("SAVE10")).thenReturn(preview);
        CouponController controller = new CouponController(useCases);

        ResponseEntity<CouponDtos.PreviewResponse> response = controller.preview("SAVE10");

        assertThat(response.getBody()).isEqualTo(preview);
        verify(useCases).preview("SAVE10");
    }
}
