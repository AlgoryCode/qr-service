package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillingAddress;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.dto.BillingAddressDtos;
import com.ael.algoryqrservice.model.dto.AddressDto;
import com.ael.algoryqrservice.repository.BillingAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingAddressService {
    private final BillingAddressRepository repository;

    @Transactional(readOnly = true)
    public List<BillingAddressDtos.Response> list(Long userId) {
        return repository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BillingAddressDtos.Response create(Long userId, BillingAddressDtos.Request request) {
        BillingAddress address = apply(BillingAddress.builder().userId(userId).build(), request);
        if (request.defaultAddress() || repository.findByUserIdAndDefaultAddressTrue(userId).isEmpty()) {
            clearDefault(userId);
            address.setDefaultAddress(true);
        }
        return toResponse(repository.save(address));
    }

    @Transactional
    public BillingAddressDtos.Response update(Long userId, Long id, BillingAddressDtos.Request request) {
        BillingAddress address = owned(userId, id);
        boolean wasDefault = address.isDefaultAddress();
        apply(address, request);
        if (request.defaultAddress()) {
            clearDefault(userId);
            address.setDefaultAddress(true);
        } else if (wasDefault) {
            address.setDefaultAddress(true);
        }
        return toResponse(repository.save(address));
    }

    @Transactional
    public BillingAddressDtos.Response makeDefault(Long userId, Long id) {
        BillingAddress address = owned(userId, id);
        clearDefault(userId);
        address.setDefaultAddress(true);
        return toResponse(repository.save(address));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        BillingAddress address = owned(userId, id);
        boolean wasDefault = address.isDefaultAddress();
        repository.delete(address);
        repository.flush();
        if (wasDefault) {
            repository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefaultAddress(true);
                        repository.save(next);
                    });
        }
    }

    @Transactional
    public BillingSnapshot resolveSnapshot(Long userId, Long id, BillingAddressDtos.Request inline) {
        if ((id == null) == (inline == null)) {
            throw new BadRequestException("billingAddressId veya inlineBillingAddress alanlarından yalnızca biri gönderilmelidir");
        }
        BillingAddress address = id != null ? owned(userId, id) : createEntity(userId, inline);
        if (id == null) {
            address = repository.save(address);
        }
        return toSnapshot(address);
    }

    public BillingSnapshot legacySnapshot(Long userId, AddressDto address, String identityNumber) {
        return BillingSnapshot.builder()
                .type(com.ael.algoryqrservice.model.enums.BillingAddressType.INDIVIDUAL)
                .name(address.getContactName())
                .tckn(identityNumber)
                .country(address.getCountry())
                .city(address.getCity())
                .address(address.getAddress())
                .postcode(address.getZipCode())
                .build();
    }

    private BillingAddress createEntity(Long userId, BillingAddressDtos.Request request) {
        return apply(BillingAddress.builder().userId(userId).build(), request);
    }

    private BillingAddress apply(BillingAddress address, BillingAddressDtos.Request request) {
        address.setType(request.type());
        address.setName(request.name());
        address.setSurname(request.surname());
        address.setLegalName(request.legalName());
        address.setTckn(request.tckn());
        address.setVkn(request.vkn());
        address.setTaxOffice(request.taxOffice());
        address.setMersis(request.mersis());
        address.setCountry(request.country());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setAddress(request.address());
        address.setPostcode(request.postcode());
        address.setEmail(request.email());
        address.setPhone(request.phone());
        address.setTaxpayerInvoice(request.taxpayerInvoice());
        address.setDefaultAddress(request.defaultAddress());
        return address;
    }

    private void clearDefault(Long userId) {
        repository.findByUserIdAndDefaultAddressTrue(userId).ifPresent(current -> {
            current.setDefaultAddress(false);
            repository.save(current);
        });
    }

    private BillingAddress owned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BadRequestException("Fatura adresi bulunamadı"));
    }

    private BillingSnapshot toSnapshot(BillingAddress address) {
        return BillingSnapshot.builder()
                .billingAddressId(address.getId()).type(address.getType()).name(address.getName())
                .surname(address.getSurname()).legalName(address.getLegalName()).tckn(address.getTckn())
                .vkn(address.getVkn()).taxOffice(address.getTaxOffice()).mersis(address.getMersis())
                .country(address.getCountry()).city(address.getCity()).district(address.getDistrict())
                .address(address.getAddress()).postcode(address.getPostcode()).email(address.getEmail())
                .phone(address.getPhone()).taxpayerInvoice(address.isTaxpayerInvoice()).build();
    }

    private BillingAddressDtos.Response toResponse(BillingAddress address) {
        return new BillingAddressDtos.Response(address.getId(), address.getType(), address.getName(),
                address.getSurname(), address.getLegalName(), address.getTckn(), address.getVkn(),
                address.getTaxOffice(), address.getMersis(), address.getCountry(), address.getCity(),
                address.getDistrict(), address.getAddress(), address.getPostcode(), address.getEmail(),
                address.getPhone(), address.isTaxpayerInvoice(), address.isDefaultAddress(),
                address.getCreatedAt(), address.getUpdatedAt());
    }
}
