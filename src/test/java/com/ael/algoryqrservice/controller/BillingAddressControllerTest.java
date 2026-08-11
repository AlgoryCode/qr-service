package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.BillingAddressDtos;
import com.ael.algoryqrservice.model.enums.BillingAddressType;
import com.ael.algoryqrservice.service.BillingAddressService;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BillingAddressControllerTest {
    @Test
    void list_whenAuthenticated_thenUseCurrentUserOwnershipBoundary() {
        BillingAddressService service = mock(BillingAddressService.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(7L).build());
        BillingAddressDtos.Response response = sampleResponse(4L, true);
        when(service.list(7L)).thenReturn(List.of(response));
        BillingAddressController controller = new BillingAddressController(service, securityUtils);

        List<BillingAddressDtos.Response> result = controller.list();

        assertThat(result).containsExactly(response);
        verify(service).list(7L);
    }

    @Test
    void get_whenAuthenticated_thenDelegateWithCurrentUser() {
        BillingAddressService service = mock(BillingAddressService.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(7L).build());
        BillingAddressDtos.Response response = sampleResponse(4L, true);
        when(service.get(7L, 4L)).thenReturn(response);
        BillingAddressController controller = new BillingAddressController(service, securityUtils);

        BillingAddressDtos.Response result = controller.get(4L);

        assertThat(result).isEqualTo(response);
        verify(service).get(7L, 4L);
    }

    @Test
    void makeDefault_whenAuthenticated_thenDelegateWithCurrentUser() {
        BillingAddressService service = mock(BillingAddressService.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(7L).build());
        BillingAddressDtos.Response response = sampleResponse(4L, true);
        when(service.makeDefault(7L, 4L)).thenReturn(response);
        BillingAddressController controller = new BillingAddressController(service, securityUtils);

        BillingAddressDtos.Response result = controller.makeDefault(4L);

        assertThat(result).isEqualTo(response);
        verify(service).makeDefault(7L, 4L);
    }

    private static BillingAddressDtos.Response sampleResponse(Long id, boolean defaultAddress) {
        return new BillingAddressDtos.Response(
                id, BillingAddressType.INDIVIDUAL, "Ev", "Ada", "Lovelace", null, null, null, null,
                null, "TR", "İstanbul", "Kadıköy", "Adres", "34000", "ada@example.com",
                "5551112233", false, defaultAddress, null, null);
    }
}
