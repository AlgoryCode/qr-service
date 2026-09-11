package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PurchaseInitiateResponse;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.service.AddonPurchaseService;
import com.ael.algoryqrservice.service.EntitlementService;
import com.ael.algoryqrservice.service.PurchaseLogService;
import com.ael.algoryqrservice.service.PurchaseService;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseControllerCouponTest {

    @Test
    void purchase_whenCouponCode_thenForwardToService() {
        PurchaseService purchaseService = mock(PurchaseService.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        User user = User.builder().id(7L).build();
        when(securityUtils.getCurrentUser()).thenReturn(user);
        PurchaseRequest request = new PurchaseRequest();
        request.setCouponCode("SAVE10");
        PurchaseInitiateResponse initiated = PurchaseInitiateResponse.builder()
                .purchaseId(10L)
                .status(PurchaseStatus.PENDING)
                .build();
        when(purchaseService.purchase(user, request, "127.0.0.1")).thenReturn(initiated);
        PurchaseController controller = new PurchaseController(
                purchaseService,
                mock(AddonPurchaseService.class),
                mock(PurchaseLogService.class),
                mock(EntitlementService.class),
                securityUtils
        );
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("127.0.0.1");

        ResponseEntity<PurchaseInitiateResponse> response = controller.purchase(request, http);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(initiated);
        verify(purchaseService).purchase(user, request, "127.0.0.1");
    }
}
