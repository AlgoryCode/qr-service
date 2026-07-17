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
        BillingAddressDtos.Response response = new BillingAddressDtos.Response(
                4L, BillingAddressType.INDIVIDUAL, "Ada", "Lovelace", null, null, null, null,
                null, "TR", "İstanbul", "Kadıköy", "Adres", "34000", "ada@example.com",
                "5551112233", false, true, null, null);
        when(service.list(7L)).thenReturn(List.of(response));
        BillingAddressController controller = new BillingAddressController(service, securityUtils);

        List<BillingAddressDtos.Response> result = controller.list();

        assertThat(result).containsExactly(response);
        verify(service).list(7L);
    }
}
