package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreCourier;
import com.ael.algoryqrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.store.repository.StoreCourierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreCourierService {

    private final StoreCourierRepository storeCourierRepository;
    private final MerchantService merchantService;

    @Transactional(readOnly = true)
    public List<StoreDtos.CourierResponse> list() {
        Merchant merchant = merchantService.requireCurrentMerchant();
        return storeCourierRepository.findByMerchantIdAndDeletedFalseOrderByFullNameAsc(merchant.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StoreDtos.CourierResponse create(StoreDtos.CourierRequest request) {
        Merchant merchant = merchantService.requireCurrentMerchant();
        StoreCourier courier = StoreCourier.builder()
                .merchantId(merchant.getId())
                .fullName(request.fullName().trim())
                .phone(request.phone().trim())
                .vehicleType(request.vehicleType())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(storeCourierRepository.save(courier));
    }

    @Transactional
    public StoreDtos.CourierResponse update(Long courierId, StoreDtos.CourierRequest request) {
        StoreCourier courier = requireOwned(courierId);
        courier.setFullName(request.fullName().trim());
        courier.setPhone(request.phone().trim());
        courier.setVehicleType(request.vehicleType());
        if (request.active() != null) {
            courier.setActive(request.active());
        }
        return toResponse(storeCourierRepository.save(courier));
    }

    @Transactional
    public void delete(Long courierId) {
        StoreCourier courier = requireOwned(courierId);
        courier.setDeleted(true);
        courier.setActive(false);
        storeCourierRepository.save(courier);
    }

    private StoreCourier requireOwned(Long courierId) {
        Merchant merchant = merchantService.requireCurrentMerchant();
        return storeCourierRepository.findByIdAndMerchantIdAndDeletedFalse(courierId, merchant.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kurye bulunamadı"));
    }

    private StoreDtos.CourierResponse toResponse(StoreCourier courier) {
        return StoreDtos.CourierResponse.builder()
                .id(courier.getId())
                .fullName(courier.getFullName())
                .phone(courier.getPhone())
                .vehicleType(courier.getVehicleType())
                .active(courier.isActive())
                .build();
    }
}
