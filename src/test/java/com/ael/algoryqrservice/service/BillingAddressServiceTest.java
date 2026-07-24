package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.BillingAddress;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.dto.BillingAddressDtos;
import com.ael.algoryqrservice.model.enums.BillingAddressType;
import com.ael.algoryqrservice.repository.BillingAddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingAddressServiceTest {
    @Mock BillingAddressRepository repository;
    @InjectMocks BillingAddressService service;

    @Test
    void get_whenAddressOwnedByAnotherUser_thenNotFound() {
        when(repository.findByIdAndUserId(4L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(7L, 4L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Fatura adresi bulunamadı");
    }

    @Test
    void get_whenOwned_thenReturnResponse() {
        BillingAddress address = sampleCorporate(4L, 7L, true);
        when(repository.findByIdAndUserId(4L, 7L)).thenReturn(Optional.of(address));

        BillingAddressDtos.Response response = service.get(7L, 4L);

        assertThat(response.id()).isEqualTo(4L);
        assertThat(response.legalName()).isEqualTo("Algory");
        assertThat(response.defaultAddress()).isTrue();
    }

    @Test
    void resolveSnapshot_whenAddressOwnedByAnotherUser_thenNotFound() {
        when(repository.findByIdAndUserId(4L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveSnapshot(7L, 4L, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Fatura adresi bulunamadı");
    }

    @Test
    void resolveSnapshot_whenBothIdAndInline_thenBadRequest() {
        BillingAddressDtos.Request inline = individualRequest(false);

        assertThatThrownBy(() -> service.resolveSnapshot(7L, 4L, inline))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("billingAddressId");
    }

    @Test
    void resolveSnapshot_whenOwnedAddressChangesLater_thenSnapshotRemainsImmutable() {
        BillingAddress address = sampleCorporate(4L, 7L, false);
        when(repository.findByIdAndUserId(4L, 7L)).thenReturn(Optional.of(address));

        BillingSnapshot snapshot = service.resolveSnapshot(7L, 4L, null);
        address.setAddress("Adres 2");

        assertThat(snapshot.getAddress()).isEqualTo("Adres 1");
        assertThat(snapshot.getBillingAddressId()).isEqualTo(4L);
        assertThat(snapshot.getVkn()).isEqualTo("1234567890");
    }

    @Test
    void create_whenFirstAddress_thenMakeDefault() {
        BillingAddressDtos.Request request = individualRequest(false);
        when(repository.findByUserIdAndDefaultAddressTrue(7L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BillingAddressDtos.Response response = service.create(7L, request);

        assertThat(response.defaultAddress()).isTrue();
    }

    @Test
    void makeDefault_whenOwned_thenClearPreviousAndSetActive() {
        BillingAddress previous = sampleCorporate(1L, 7L, true);
        BillingAddress next = sampleCorporate(2L, 7L, false);
        when(repository.findByIdAndUserId(2L, 7L)).thenReturn(Optional.of(next));
        when(repository.findByUserIdAndDefaultAddressTrue(7L)).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BillingAddressDtos.Response response = service.makeDefault(7L, 2L);

        assertThat(previous.isDefaultAddress()).isFalse();
        assertThat(response.defaultAddress()).isTrue();
        assertThat(response.id()).isEqualTo(2L);
        verify(repository).save(previous);
        verify(repository).save(next);
    }

    @Test
    void list_whenAddressesExist_thenPreserveRepositoryOrder() {
        BillingAddress active = sampleCorporate(2L, 7L, true);
        BillingAddress other = sampleCorporate(1L, 7L, false);
        when(repository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(7L))
                .thenReturn(List.of(active, other));

        List<BillingAddressDtos.Response> result = service.list(7L);

        assertThat(result).extracting(BillingAddressDtos.Response::id).containsExactly(2L, 1L);
        assertThat(result.getFirst().defaultAddress()).isTrue();
    }

    @Test
    void create_whenMarkedDefault_thenClearPreviousDefault() {
        BillingAddress previous = sampleCorporate(1L, 7L, true);
        BillingAddressDtos.Request request = individualRequest(true);
        when(repository.findByUserIdAndDefaultAddressTrue(7L)).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> {
            BillingAddress saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(9L);
            }
            return saved;
        });

        BillingAddressDtos.Response response = service.create(7L, request);

        ArgumentCaptor<BillingAddress> captor = ArgumentCaptor.forClass(BillingAddress.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(previous.isDefaultAddress()).isFalse();
        assertThat(response.defaultAddress()).isTrue();
        assertThat(captor.getAllValues()).anyMatch(BillingAddress::isDefaultAddress);
    }

    private static BillingAddress sampleCorporate(Long id, Long userId, boolean defaultAddress) {
        return BillingAddress.builder().id(id).userId(userId)
                .type(BillingAddressType.CORPORATE).legalName("Algory")
                .vkn("1234567890").taxOffice("Merkez").country("TR").city("İstanbul")
                .district("Kadıköy").address("Adres 1").postcode("34000")
                .email("billing@example.com").phone("5551112233")
                .defaultAddress(defaultAddress).build();
    }

    private static BillingAddressDtos.Request individualRequest(boolean defaultAddress) {
        return new BillingAddressDtos.Request(
                BillingAddressType.INDIVIDUAL, "Ada", "Lovelace", null, null, null, null, null,
                "TR", "İstanbul", "Kadıköy", "Adres", "34000", "ada@example.com", "5551112233",
                false, defaultAddress);
    }
}
