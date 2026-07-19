package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrNameRequest;
import com.ael.algoryqrservice.model.dto.QrNameResponse;
import com.ael.algoryqrservice.model.dto.QrListResponse;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.provider.QrProvider;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.WriterException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QrService {


    private final QrProviderFactory qrProviderFactory;
    private final QrRepository qrRepository;
    private final MenuRepository menuRepository;
    private final ObjectMapper objectMapper;
    private final EntitlementService entitlementService;
    private final SecurityUtils securityUtils;

    public <T extends QrRequest> QrResponse createQR(T req, Long userId) throws IOException, WriterException {
        entitlementService.requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        Type qrType = Type.from(req.getType());
        if (qrType == Type.MENU) {
            if (menuRepository.existsActiveLiveMenuQrForUser(userId)
                    && entitlementService.hasUsableQrMenuPackage(userId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Aktif bir dijital menü QR kaydınız zaten var"
                );
            }
            entitlementService.requireScope(userId, CatalogScopes.QR_MENU_OWNER);
            entitlementService.consume(userId, CatalogProducts.QR_MENU, 1);
        }
        entitlementService.consume(userId, CatalogProducts.QR_CREATE, 1);
        req.setUserId(userId);

        QrProvider<T> provider = qrProviderFactory.get(qrType,(Class<T>) req.getClass());
        return provider.createQr(req);
    }

    public QrResponse updateQr(Long qrId, QrRequest req) throws IOException, WriterException {
        Qr existingQr = qrRepository.findById(qrId)
                .orElseThrow(() -> new EntityNotFoundException("QR bulunamadı: " + qrId));
        requireOwnership(existingQr);

        if (existingQr.isDeleted()) {
            throw new EntityNotFoundException("QR zaten silinmiş: " + qrId);
        }

        if (req.getType() == null || req.getType().isBlank()) {
            throw new IllegalArgumentException("type alanı zorunludur");
        }

        if (req.getUserId() != null && !req.getUserId().equals(existingQr.getUserId())) {
            throw new IllegalArgumentException("QR başka bir kullanıcıya ait, userId değiştirilemez");
        }

        req.setUserId(existingQr.getUserId());

        softDeleteQrAndLinkedMenu(existingQr);

        return createQR(req, existingQr.getUserId());
    }

    public List<QrListResponse> getUserQrs(Long userId) {
        Long currentUserId = securityUtils.getCurrentUser().getId();
        if (!currentUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Başka kullanıcının QR kayıtlarına erişilemez");
        }
        List<Qr> qrs = qrRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
        Set<Long> activeMenuQrIds = loadActiveMenuQrIds(userId, qrs);

        return qrs
                .stream()
                .filter(qr -> shouldShowQr(qr, activeMenuQrIds))
                .map(this::mapToListResponse)
                .toList();
    }

    private Set<Long> loadActiveMenuQrIds(Long userId, List<Qr> qrs) {
        Set<Long> menuQrIds = qrs.stream()
                .filter(this::isMenuQr)
                .map(Qr::getQrId)
                .collect(Collectors.toSet());
        if (menuQrIds.isEmpty()) {
            return Set.of();
        }
        return menuRepository.findActiveQrIdsByUserIdAndQrIdIn(userId, menuQrIds);
    }

    private boolean shouldShowQr(Qr qr, Set<Long> activeMenuQrIds) {
        return !isMenuQr(qr) || activeMenuQrIds.contains(qr.getQrId());
    }

    private boolean isMenuQr(Qr qr) {
        if (qr.getQrType() != null
                && Type.MENU.getValue().equalsIgnoreCase(qr.getQrType().getTypeName())) {
            return true;
        }
        JsonNode details = qr.getDetails();
        return details != null
                && ((details.has("themeId") && details.has("businessName"))
                || (details.has("menuId") && !details.get("menuId").isNull())
                || (details.has("type") && Type.MENU.getValue().equalsIgnoreCase(details.get("type").asText())));
    }

    public QrNameResponse updateQrName(Long qrId, QrNameRequest req) {
        if (req == null || req.getQrName() == null || req.getQrName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qrName zorunludur");
        }

        Qr existingQr = qrRepository.findById(qrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR bulunamadı: " + qrId));
        requireOwnership(existingQr);

        if (existingQr.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR zaten silinmiş: " + qrId);
        }

        existingQr.setQrName(req.getQrName());
        qrRepository.save(existingQr);

        return QrNameResponse.builder()
                .qrId(existingQr.getQrId())
                .qrName(existingQr.getQrName())
                .build();
    }

    private QrListResponse mapToListResponse(Qr qr) {
        return QrListResponse.builder()
                .qrId(qr.getQrId())
                .userId(qr.getUserId())
                .qrName(qr.getQrName())
                .imgSrc(qr.getImgSrc())
                .details(objectMapper.convertValue(qr.getDetails(), new TypeReference<Map<String, Object>>() {}))
                .createdAt(qr.getCreatedAt())
                .build();
    }

    public void deleteQrByQrId(Long qrId){
        Qr qr = qrRepository.findById(qrId)
                .orElseThrow(() -> new EntityNotFoundException("QR bulunamadı: " + qrId));
        requireOwnership(qr);
        softDeleteQrAndLinkedMenu(qr);
    }

    private void softDeleteQrAndLinkedMenu(Qr qr) {
        qr.setDeleted(true);
        qrRepository.save(qr);
        menuRepository.findByQrIdAndDeletedFalse(qr.getQrId()).ifPresent(menu -> {
            menu.setDeleted(true);
            menu.setActive(false);
            menuRepository.save(menu);
        });
    }

    private void requireOwnership(Qr qr) {
        Long currentUserId = securityUtils.getCurrentUser().getId();
        if (!currentUserId.equals(qr.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu QR kaydına erişim yetkiniz yok");
        }
    }
}
