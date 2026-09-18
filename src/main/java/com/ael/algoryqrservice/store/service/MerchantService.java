package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantMapper merchantMapper;
    private final StoreSlugGenerator storeSlugGenerator;
    private final StoreTokenGenerator storeTokenGenerator;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public Merchant requireCurrentMerchant() {
        return merchantRepository.findByUserIdAndDeletedFalse(securityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mağaza kurulumu yapılmamış"));
    }

    @Transactional(readOnly = true)
    public Optional<StoreDtos.MerchantResponse> findMine() {
        return merchantRepository.findByUserIdAndDeletedFalse(securityUtils.getCurrentUserId())
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

    @Transactional
    public StoreDtos.MerchantResponse rotateToken() {
        Merchant merchant = requireCurrentMerchant();
        merchant.setPublicToken(storeTokenGenerator.generateUnique(merchantRepository::existsByPublicToken));
        return merchantMapper.toResponse(merchantRepository.save(merchant));
    }
}
