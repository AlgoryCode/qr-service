package com.ael.algoryqrservice.qrservice.security;

import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.client.dto.ExternalProductAccessResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductUsageGatewayCacheTest {

    private final FulfillmentServiceClient client = mock(FulfillmentServiceClient.class);
    private final ProductUsageGateway gateway = new ProductUsageGateway(client,
            mock(com.ael.algoryqrservice.qrservice.billing.service.UpgradeOptionService.class),
            mock(com.ael.algoryqrservice.qrservice.product.repository.ProductRepository.class),
            mock(com.ael.algoryqrservice.qrservice.repository.FeatureRepository.class));

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void oneRequestAsksFulfillmentOncePerProduct() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(client.findProductAccess(7L, "QR_MENU")).thenReturn(new ExternalProductAccessResponse(true, "PRO", "QR_MENU", null));

        gateway.allow(7L, "QR_MENU");
        gateway.allow(7L, "QR_MENU");

        verify(client, times(1)).findProductAccess(7L, "QR_MENU");
    }

    @Test
    void nextRequestSeesAFreshAnswer() {
        when(client.findProductAccess(7L, "QR_MENU")).thenReturn(new ExternalProductAccessResponse(true, "PRO", "QR_MENU", null));

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        gateway.allow(7L, "QR_MENU");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        gateway.allow(7L, "QR_MENU");

        verify(client, times(2)).findProductAccess(7L, "QR_MENU");
    }

    @Test
    void outsideARequestNothingIsCached() {
        when(client.findProductAccess(7L, "QR_MENU")).thenReturn(new ExternalProductAccessResponse(true, "PRO", "QR_MENU", null));

        gateway.allow(7L, "QR_MENU");
        gateway.allow(7L, "QR_MENU");

        verify(client, times(2)).findProductAccess(7L, "QR_MENU");
    }
}
