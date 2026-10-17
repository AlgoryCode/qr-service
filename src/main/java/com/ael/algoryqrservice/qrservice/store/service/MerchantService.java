package com.ael.algoryqrservice.qrservice.store.service;

import com.ael.algoryqrservice.qrservice.store.model.Merchant;
import com.ael.algoryqrservice.qrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.qrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import com.ael.algoryqrservice.qrservice.store.service.StoreSlugGenerator;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantMapper merchantMapper;
    private final StoreSlugGenerator storeSlugGenerator;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public Merchant requireCurrentMerchant() {
        return merchantRepository.findByOwnerMerchantIdAndDeletedFalse(securityUtils.getCurrentMerchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mağaza kurulumu yapılmamış"));
    }

    @Transactional(readOnly = true)
    public Optional<StoreDtos.MerchantResponse> findMine() {
        return merchantRepository.findByOwnerMerchantIdAndDeletedFalse(securityUtils.getCurrentMerchantId())
                .map(merchantMapper::toResponse);
    }

    @Transactional
    public StoreDtos.MerchantResponse update(StoreDtos.UpdateMerchantRequest request) {
        Merchant merchant = requireCurrentMerchant();
        if (request.business() != null) {
            merchantMapper.applyBusinessInfo(merchant, request.business());
            merchant.setSlug(storeSlugGenerator.generate(merchant.getBusinessName()));
        }
        if (request.delivery() != null) {
            merchantMapper.applyDeliverySettings(merchant, request.delivery());
        }
        if (request.manuallyClosed() != null) {
            merchant.setManuallyClosed(request.manuallyClosed());
        }
        if (request.status() != null) {
            merchant.setStatus(request.status());
        }
        return merchantMapper.toResponse(merchantRepository.save(merchant));
    }
}
