package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillingAddress;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.dto.BillingAddressDtos;
import com.ael.algoryqrservice.model.enums.BillingAddressType;
import com.ael.algoryqrservice.repository.BillingAddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingAddressServiceTest {
    @Mock BillingAddressRepository repository;
    @InjectMocks BillingAddressService service;

    @Test
    void resolveSnapshot_whenAddressOwnedByAnotherUser_thenReject() {
        when(repository.findByIdAndUserId(4L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveSnapshot(7L, 4L, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolveSnapshot_whenOwnedAddressChangesLater_thenSnapshotRemainsImmutable() {
        BillingAddress address = BillingAddress.builder().id(4L).userId(7L)
                .type(BillingAddressType.CORPORATE).legalName("Algory")
                .vkn("1234567890").taxOffice("Merkez").country("TR").city("İstanbul")
                .district("Kadıköy").address("Adres 1").postcode("34000")
                .email("billing@example.com").phone("5551112233").build();
        when(repository.findByIdAndUserId(4L, 7L)).thenReturn(Optional.of(address));

        BillingSnapshot snapshot = service.resolveSnapshot(7L, 4L, null);
        address.setAddress("Adres 2");

        assertThat(snapshot.getAddress()).isEqualTo("Adres 1");
        assertThat(snapshot.getVkn()).isEqualTo("1234567890");
    }

    @Test
    void create_whenFirstAddress_thenMakeDefault() {
        BillingAddressDtos.Request request = new BillingAddressDtos.Request(
                BillingAddressType.INDIVIDUAL, "Ada", "Lovelace", null, null, null, null, null,
                "TR", "İstanbul", "Kadıköy", "Adres", "34000", "ada@example.com", "5551112233",
                false, false);
        when(repository.findByUserIdAndDefaultAddressTrue(7L)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        BillingAddressDtos.Response response = service.create(7L, request);

        assertThat(response.defaultAddress()).isTrue();
    }
}
