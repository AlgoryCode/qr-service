package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.MerchantStatus;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
public class StorefrontResolver {

    private final MerchantRepository merchantRepository;
    private final MenuRepository menuRepository;

    /**
     * A wrong token and a missing store must be indistinguishable, so every failure
     * below answers 404 rather than 403.
     */
    @Transactional(readOnly = true)
    public Merchant requirePublished(Long storeNo, String token) {
        Merchant merchant = merchantRepository.findByStoreNoAndDeletedFalse(storeNo)
                .orElseThrow(StorefrontResolver::notFound);
        if (!tokenMatches(merchant.getPublicToken(), token)) {
            throw notFound();
        }
        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw notFound();
        }
        Menu catalogMenu = menuRepository.findById(merchant.getCatalogMenuId())
                .filter(menu -> !menu.isDeleted())
                .orElseThrow(StorefrontResolver::notFound);
        if (!catalogMenu.isActive() || !catalogMenu.isPublicAccessEnabled()) {
            throw notFound();
        }
        return merchant;
    }

    private boolean tokenMatches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Mağaza bulunamadı");
    }
}
