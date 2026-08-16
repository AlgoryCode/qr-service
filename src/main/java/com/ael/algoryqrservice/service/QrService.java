package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.ConsumedEntitlement;
import com.ael.algoryqrservice.model.dto.QrActiveRequest;
import com.ael.algoryqrservice.model.dto.QrActiveResponse;
import com.ael.algoryqrservice.model.dto.QrNameRequest;
import com.ael.algoryqrservice.model.dto.QrNameResponse;
import com.ael.algoryqrservice.model.dto.QrListPageResponse;
import com.ael.algoryqrservice.model.dto.QrListResponse;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.model.enums.QrListScope;
import com.ael.algoryqrservice.provider.QrProvider;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QrService {


    private final QrProviderFactory qrProviderFactory;
    private final QrRepository qrRepository;
    private final MenuRepository menuRepository;
    private final PurchaseRepository purchaseRepository;
    private final ObjectMapper objectMapper;
    private final EntitlementService entitlementService;
    private final MenuQrSoftDeleteService menuQrSoftDeleteService;
    private final SecurityUtils securityUtils;

    public <T extends QrRequest> QrResponse createQR(T req, Long userId) throws IOException, WriterException {
        entitlementService.requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        Type qrType = Type.from(req.getType());
        if (qrType == Type.MENU) {
            entitlementService.requireScope(userId, CatalogScopes.QR_MENU_OWNER);
            entitlementService.consume(userId, CatalogProducts.QR_MENU, 1);
        }
        ConsumedEntitlement consumed = entitlementService.consume(userId, CatalogProducts.QR_CREATE, 1);
        if (consumed != null && consumed.purchaseId() != null) {
            req.setPurchaseId(consumed.purchaseId());
        }
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

    public QrListPageResponse getUserQrs(
            Long userId,
            boolean includeImage,
            int page,
            int size,
            QrListScope scope
    ) {
        Long currentUserId = securityUtils.getCurrentUser().getId();
        if (!currentUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Başka kullanıcının QR kayıtlarına erişilemez");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<Qr> qrs = qrRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
        Long activePurchaseId = entitlementService.resolveActivePurchaseId(userId);
        Map<Long, Purchase> purchasesById = loadPurchasesForQrs(qrs);

        List<QrListResponse> filtered = qrs
                .stream()
                .filter(qr -> !isMenuQr(qr))
                .filter(qr -> matchesScope(qr, scope, activePurchaseId))
                .map(qr -> mapToListResponse(qr, includeImage, activePurchaseId, purchasesById))
                .toList();

        long totalElements = filtered.size();
        int totalPages = (int) Math.max(1, (totalElements + safeSize - 1) / safeSize);
        if (totalElements == 0) {
            totalPages = 0;
        }
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        List<QrListResponse> content = filtered.subList(from, to);

        return QrListPageResponse.builder()
                .content(content)
                .page(safePage)
                .size(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(safePage + 1 < totalPages)
                .build();
    }

    private Map<Long, Purchase> loadPurchasesForQrs(List<Qr> qrs) {
        List<Long> purchaseIds = qrs.stream()
                .map(Qr::getPurchaseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (purchaseIds.isEmpty()) {
            return Map.of();
        }
        return purchaseRepository.findAllById(purchaseIds).stream()
                .collect(Collectors.toMap(Purchase::getId, Function.identity()));
    }

    private boolean matchesScope(Qr qr, QrListScope scope, Long activePurchaseId) {
        if (scope == null || scope == QrListScope.ALL) {
            return true;
        }
        Long purchaseId = qr.getPurchaseId();
        if (scope == QrListScope.CURRENT) {
            return activePurchaseId != null && Objects.equals(purchaseId, activePurchaseId);
        }
        return purchaseId == null || activePurchaseId == null || !Objects.equals(purchaseId, activePurchaseId);
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

    private QrListResponse mapToListResponse(
            Qr qr,
            boolean includeImage,
            Long activePurchaseId,
            Map<Long, Purchase> purchasesById
    ) {
        Long purchaseId = qr.getPurchaseId();
        Purchase purchase = purchaseId != null ? purchasesById.get(purchaseId) : null;
        boolean activePackage = activePurchaseId != null && Objects.equals(purchaseId, activePurchaseId);
        boolean legacy = purchaseId == null || !activePackage;
        boolean menuQr = isMenuQr(qr);
        Long menuId = null;
        if (menuQr) {
            menuId = menuRepository.findByQrIdAndDeletedFalse(qr.getQrId())
                    .map(Menu::getMenuId)
                    .orElse(null);
        }

        return QrListResponse.builder()
                .qrId(qr.getQrId())
                .userId(qr.getUserId())
                .qrName(qr.getQrName())
                .imgSrc(includeImage ? qr.getImgSrc() : null)
                .details(objectMapper.convertValue(qr.getDetails(), new TypeReference<Map<String, Object>>() {}))
                .createdAt(qr.getCreatedAt())
                .purchaseId(purchaseId)
                .packageName(purchase != null ? purchase.getPackageName() : null)
                .legacy(legacy)
                .activePackage(activePackage)
                .active(menuQr ? qr.isActive() : true)
                .menuId(menuId)
                .build();
    }

    public QrActiveResponse updateMenuQrActive(Long qrId, QrActiveRequest req) {
        if (req == null || req.getActive() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active alanı zorunludur");
        }

        Qr qr = qrRepository.findById(qrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR bulunamadı: " + qrId));
        requireOwnership(qr);

        if (qr.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR zaten silinmiş: " + qrId);
        }
        if (!isMenuQr(qr)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aktif/pasif durumu yalnızca menü QR kodları için değiştirilebilir");
        }

        boolean nextActive = req.getActive();
        if (nextActive == qr.isActive()) {
            return QrActiveResponse.builder()
                    .qrId(qr.getQrId())
                    .active(qr.isActive())
                    .build();
        }

        Menu menu = menuRepository.findByQrIdAndDeletedFalse(qr.getQrId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));

        if (nextActive) {
            entitlementService.assertMenuActivationAllowed(qr.getUserId());
        }

        qr.setActive(nextActive);
        menu.setActive(nextActive);
        qrRepository.save(qr);
        menuRepository.save(menu);
        entitlementService.syncMenuEntitlements(qr.getUserId());

        return QrActiveResponse.builder()
                .qrId(qr.getQrId())
                .active(qr.isActive())
                .build();
    }

    public void deleteQrByQrId(Long qrId){
        Qr qr = qrRepository.findById(qrId)
                .orElseThrow(() -> new EntityNotFoundException("QR bulunamadı: " + qrId));
        requireOwnership(qr);
        if (qr.isDeleted()) {
            throw new EntityNotFoundException("QR zaten silinmiş: " + qrId);
        }
        if (isMenuQr(qr)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Menü QR kodları buradan silinemez. Menüyü silmek için Dijital Menü bölümünü kullanın."
            );
        }
        softDeleteQrAndLinkedMenu(qr);
    }

    @Transactional
    public void softDeleteMenuQr(Long qrId) {
        Qr qr = qrRepository.findById(qrId)
                .orElseThrow(() -> new EntityNotFoundException("QR bulunamadı: " + qrId));
        requireOwnership(qr);
        if (qr.isDeleted()) {
            throw new EntityNotFoundException("QR zaten silinmiş: " + qrId);
        }
        if (!isMenuQr(qr)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bu işlem yalnızca menü QR kodları için kullanılabilir"
            );
        }
        menuQrSoftDeleteService.softDeleteMenuQr(qr);
    }

    private void softDeleteQrAndLinkedMenu(Qr qr) {
        if (qr.isDeleted()) {
            return;
        }

        boolean menuQr = isMenuQr(qr);
        if (menuQr) {
            menuQrSoftDeleteService.softDeleteMenuQr(qr);
            return;
        }

        qr.setDeleted(true);
        qrRepository.save(qr);

        menuRepository.findByQrIdAndDeletedFalse(qr.getQrId()).ifPresent(menu -> {
            if (!menu.isDeleted()) {
                menu.setDeleted(true);
                menuRepository.save(menu);
            }
        });

        entitlementService.syncQrCreateEntitlements(qr.getUserId());
    }

    private void requireOwnership(Qr qr) {
        Long currentUserId = securityUtils.getCurrentUser().getId();
        if (!currentUserId.equals(qr.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu QR kaydına erişim yetkiniz yok");
        }
    }
}
