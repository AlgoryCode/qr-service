package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.coupon.CouponUseCases;
import com.ael.algoryqrservice.model.DashboardUser;
import com.ael.algoryqrservice.model.dto.CouponDtos;
import com.ael.algoryqrservice.model.enums.CouponDiscountType;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.util.DashboardSecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCouponControllerTest {

    @Test
    void create_whenAdmin_thenCreated() {
        CouponUseCases useCases = mock(CouponUseCases.class);
        DashboardSecurityUtils security = mock(DashboardSecurityUtils.class);
        when(security.getCurrentDashboardUser()).thenReturn(DashboardUser.builder().id(8L).build());
        CouponDtos.CreateRequest request = new CouponDtos.CreateRequest(
                "SAVE10",
                CouponDiscountType.PERCENT,
                new BigDecimal("10"),
                LocalDateTime.now().plusDays(5),
                null
        );
        CouponDtos.Response created = CouponDtos.Response.builder()
                .id(1L)
                .code("SAVE10")
                .status(CouponStatus.UNUSED)
                .build();
        when(useCases.create(request, 8L)).thenReturn(created);
        AdminCouponController controller = new AdminCouponController(useCases, security);

        ResponseEntity<CouponDtos.Response> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
        verify(useCases).create(request, 8L);
    }

    @Test
    void revoke_whenPatch_thenDelegate() {
        CouponUseCases useCases = mock(CouponUseCases.class);
        DashboardSecurityUtils security = mock(DashboardSecurityUtils.class);
        CouponDtos.Response revoked = CouponDtos.Response.builder().id(1L).status(CouponStatus.REVOKED).build();
        when(useCases.revoke(1L, "REVOKED")).thenReturn(revoked);
        AdminCouponController controller = new AdminCouponController(useCases, security);

        ResponseEntity<CouponDtos.Response> response = controller.revoke(
                1L,
                new CouponDtos.RevokeRequest("REVOKED")
        );

        assertThat(response.getBody()).isEqualTo(revoked);
        verify(useCases).revoke(1L, "REVOKED");
    }
}
