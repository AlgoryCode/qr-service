package com.ael.algoryqrservice.qrservice.billing.service;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.BillingAddress;
import com.ael.algoryqrservice.qrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.qrservice.billing.dto.BillingAddressDtos;
import com.ael.algoryqrservice.qrservice.billing.dto.BillingAddressPageResponse;
import com.ael.algoryqrservice.qrservice.qr.dto.AddressDto;
import com.ael.algoryqrservice.qrservice.repository.BillingAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.ael.algoryqrservice.qrservice.model.enums.BillingAddressType;

@Service
@RequiredArgsConstructor
public class BillingAddressService {
    private final BillingAddressRepository repository;

    @Transactional(readOnly = true)
    public BillingAddressPageResponse list(Long merchantId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<BillingAddress> result = repository.findByMerchantIdOrderByDefaultAddressDescCreatedAtDesc(
                merchantId,
                PageRequest.of(safePage, safeSize)
        );
        return BillingAddressPageResponse.builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(safePage)
                .size(safeSize)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public BillingAddressDtos.Response get(Long merchantId, Long id) {
        return toResponse(owned(merchantId, id));
    }

    @Transactional
    public BillingAddressDtos.Response create(Long merchantId, BillingAddressDtos.Request request) {
        BillingAddress address = apply(BillingAddress.builder().merchantId(merchantId).build(), request);
        if (request.defaultAddress() || repository.findByMerchantIdAndDefaultAddressTrue(merchantId).isEmpty()) {
            clearDefault(merchantId);
            address.setDefaultAddress(true);
        }
        return toResponse(repository.save(address));
    }

    @Transactional
    public BillingAddressDtos.Response update(Long merchantId, Long id, BillingAddressDtos.Request request) {
        BillingAddress address = owned(merchantId, id);
        boolean wasDefault = address.isDefaultAddress();
        apply(address, request);
        if (request.defaultAddress()) {
            clearDefault(merchantId);
            address.setDefaultAddress(true);
        } else if (wasDefault) {
            address.setDefaultAddress(true);
        }
        return toResponse(repository.save(address));
    }

    @Transactional
    public BillingAddressDtos.Response makeDefault(Long merchantId, Long id) {
        BillingAddress address = owned(merchantId, id);
        clearDefault(merchantId);
        address.setDefaultAddress(true);
        return toResponse(repository.save(address));
    }

    @Transactional
    public void delete(Long merchantId, Long id) {
        BillingAddress address = owned(merchantId, id);
        boolean wasDefault = address.isDefaultAddress();
        repository.delete(address);
        repository.flush();
        if (wasDefault) {
            repository.findByMerchantIdOrderByDefaultAddressDescCreatedAtDesc(merchantId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefaultAddress(true);
                        repository.save(next);
                    });
        }
    }

    @Transactional
    public BillingSnapshot resolveSnapshot(Long merchantId, Long id, BillingAddressDtos.Request inline) {
        if ((id == null) == (inline == null)) {
            throw new BadRequestException("Fatura adresi seçimi geçersiz; billingAddressId veya inlineBillingAddress alanlarından yalnızca biri gönderilmelidir");
        }
        BillingAddress address = id != null ? owned(merchantId, id) : createEntity(merchantId, inline);
        if (id == null) {
            address = repository.save(address);
        }
        return toSnapshot(address);
    }

    @Transactional(readOnly = true)
    public BillingSnapshot resolveDefaultSnapshot(Long merchantId) {
        BillingAddress address = repository.findByMerchantIdAndDefaultAddressTrue(merchantId)
                .or(() -> repository.findByMerchantIdOrderByDefaultAddressDescCreatedAtDesc(merchantId).stream().findFirst())
                .orElseThrow(() -> new BadRequestException(
                        "Kart eklemek icin once bir fatura adresi eklemelisiniz."
                ));
        return toSnapshot(address);
    }

    public BillingSnapshot legacySnapshot(Long merchantId, AddressDto address, String identityNumber) {
        return BillingSnapshot.builder()
                .type(com.ael.algoryqrservice.qrservice.model.enums.BillingAddressType.INDIVIDUAL)
                .name(address.getContactName())
                .tckn(identityNumber)
                .country(address.getCountry())
                .city(address.getCity())
                .address(address.getAddress())
                .postcode(address.getZipCode())
                .build();
    }

    private BillingAddress createEntity(Long merchantId, BillingAddressDtos.Request request) {
        return apply(BillingAddress.builder().merchantId(merchantId).build(), request);
    }

    private BillingAddress apply(BillingAddress address, BillingAddressDtos.Request request) {
        address.setType(request.type());
        address.setTitle(request.title() == null ? null : request.title().trim());
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

    private void clearDefault(Long merchantId) {
        repository.findByMerchantIdAndDefaultAddressTrue(merchantId).ifPresent(current -> {
            current.setDefaultAddress(false);
            repository.save(current);
        });
    }

    private BillingAddress owned(Long merchantId, Long id) {
        return repository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new NotFoundException("Fatura adresi bulunamadı"));
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
        return new BillingAddressDtos.Response(address.getId(), address.getType(), address.getTitle(),
                address.getName(), address.getSurname(), address.getLegalName(), address.getTckn(),
                address.getVkn(), address.getTaxOffice(), address.getMersis(), address.getCountry(),
                address.getCity(), address.getDistrict(), address.getAddress(), address.getPostcode(),
                address.getEmail(), address.getPhone(), address.isTaxpayerInvoice(),
                address.isDefaultAddress(), address.getCreatedAt(), address.getUpdatedAt());
    }
}
