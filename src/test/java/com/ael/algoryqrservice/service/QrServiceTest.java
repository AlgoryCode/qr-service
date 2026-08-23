package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.QrType;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.ConsumedEntitlement;
import com.ael.algoryqrservice.model.dto.QrActiveRequest;
import com.ael.algoryqrservice.model.dto.QrActiveResponse;
import com.ael.algoryqrservice.model.dto.QrListPageResponse;
import com.ael.algoryqrservice.model.dto.QrListResponse;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.provider.QrProvider;
import com.ael.algoryqrservice.model.enums.QrListScope;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrServiceTest {

    @Mock
    private QrProviderFactory qrProviderFactory;
    @Mock
    private QrRepository qrRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private MenuQrSoftDeleteService menuQrSoftDeleteService;
    @Mock
    private BranchService branchService;
    @Mock
    private BranchQuotaService branchQuotaService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private QrProvider<QrRequest> qrProvider;

    @InjectMocks
    private QrService qrService;

    @Test
    void getUserQrs_whenMenuQrExists_thenExcludedFromList() {
        Long userId = 7L;
        Qr activeMenuQr = qr(1L, userId, "menu", Map.of("themeId", "classic", "businessName", "Aktif"));
        activeMenuQr.setActive(true);
        Qr passiveMenuQr = qr(2L, userId, "menu", Map.of("themeId", "classic", "businessName", "Pasif"));
        passiveMenuQr.setActive(false);
        Qr linkQr = qr(3L, userId, "link", Map.of("url", "https://example.com"));

        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());
        when(qrRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(activeMenuQr, passiveMenuQr, linkQr));

        when(entitlementService.resolveActivePurchaseId(userId)).thenReturn(10L);

        QrListPageResponse response = qrService.getUserQrs(userId, false, 0, 5, QrListScope.ALL);

        assertThat(response.getContent())
                .extracting(QrListResponse::getQrId)
                .containsExactly(linkQr.getQrId());
        assertThat(response.getContent().getFirst().getActive()).isTrue();
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void createQR_whenActiveMenuExistsAndRemainingMenuEntitlement_thenCreateProceeds() throws Exception {
        Long userId = 7L;
        QrRequest request = new QrRequest();
        request.setType("menu");
        request.setDetails(Map.of("branchId", 3L, "themeId", "classic", "businessName", "Kafe"));
        QrResponse expected = QrResponse.builder().qrId(12L).build();

        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        when(entitlementService.consume(userId, CatalogProducts.QR_CREATE, 1)).thenReturn(new ConsumedEntitlement(10L, 2L, 1));
        when(qrProviderFactory.get(any(), eq(QrRequest.class))).thenReturn(qrProvider);
        when(qrProvider.createQr(request)).thenReturn(expected);

        QrResponse response = qrService.createQR(request, userId);

        assertThat(response.getQrId()).isEqualTo(12L);
        verify(branchQuotaService).assertAndConsumeMenuCreation(userId, 3L);
        verify(entitlementService).consume(userId, CatalogProducts.QR_CREATE, 1);
    }

    @Test
    void createQR_whenMenuEntitlementExhausted_thenForbidden() {
        Long userId = 7L;
        QrRequest request = new QrRequest();
        request.setType("menu");
        request.setDetails(Map.of("branchId", 3L));

        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        doThrow(new ForbiddenException("EXTRA_MENU_REQUIRED", "Bu şubede ek menü ücretlidir. Lütfen ek menü hakkı satın alın."))
                .when(branchQuotaService).assertAndConsumeMenuCreation(userId, 3L);

        assertThatThrownBy(() -> qrService.createQR(request, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ek menü ücretlidir");

        verify(entitlementService, never()).consume(userId, CatalogProducts.QR_CREATE, 1);
    }

    @Test
    void createQR_whenNoActiveMenu_thenCreateProceeds() throws Exception {
        Long userId = 7L;
        QrRequest request = new QrRequest();
        request.setType("menu");
        request.setDetails(Map.of("branchId", 3L, "themeId", "classic", "businessName", "Kafe"));
        QrResponse expected = QrResponse.builder().qrId(11L).build();

        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        when(entitlementService.consume(userId, CatalogProducts.QR_CREATE, 1)).thenReturn(new ConsumedEntitlement(10L, 2L, 1));
        when(qrProviderFactory.get(any(), eq(QrRequest.class))).thenReturn(qrProvider);
        when(qrProvider.createQr(request)).thenReturn(expected);

        QrResponse response = qrService.createQR(request, userId);

        assertThat(response.getQrId()).isEqualTo(11L);
        verify(entitlementService).requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        verify(branchQuotaService).assertAndConsumeMenuCreation(userId, 3L);
        verify(entitlementService).consume(userId, CatalogProducts.QR_CREATE, 1);
    }

    @Test
    void deleteQrByQrId_whenMenuLinked_thenRejected() {
        Long userId = 7L;
        Qr existing = qr(5L, userId, "menu", Map.of("themeId", "classic", "businessName", "Kafe"));

        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());

        assertThatThrownBy(() -> qrService.deleteQrByQrId(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Menü QR kodları buradan silinemez");

        verify(qrRepository, never()).save(any(Qr.class));
        verify(entitlementService, never()).release(userId, CatalogProducts.QR_MENU, 1);
    }

    @Test
    void updateMenuQrActive_whenPassiveToActive_thenUpdatesQrAndMenu() {
        Long userId = 7L;
        Qr existing = qr(5L, userId, "menu", Map.of("themeId", "classic", "businessName", "Kafe"));
        existing.setActive(false);
        Menu menu = Menu.builder()
                .menuId(9L)
                .qrId(5L)
                .userId(userId)
                .branchId(3L)
                .themeId("classic")
                .businessName("Kafe")
                .active(false)
                .build();
        QrActiveRequest request = new QrActiveRequest();
        request.setActive(true);

        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());
        when(menuRepository.findByQrIdAndDeletedFalse(5L)).thenReturn(Optional.of(menu));
        doNothing().when(entitlementService).syncMenuEntitlements(userId);
        when(qrRepository.save(any(Qr.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QrActiveResponse response = qrService.updateMenuQrActive(5L, request);

        assertThat(response.getActive()).isTrue();
        assertThat(existing.isActive()).isTrue();
        assertThat(menu.isActive()).isTrue();
        verify(branchQuotaService).assertMenuActivationAllowed(userId, 3L);
        verify(entitlementService).syncMenuEntitlements(userId);
    }

    @Test
    void softDeleteMenuQr_whenMenuLinked_thenDelegatesToSoftDeleteService() {
        Long userId = 7L;
        Qr existing = qr(5L, userId, "menu", Map.of("themeId", "classic", "businessName", "Kafe"));

        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());

        qrService.softDeleteMenuQr(5L);

        verify(menuQrSoftDeleteService).softDeleteMenuQr(existing);
    }

    @Test
    void deleteQrByQrId_whenAlreadyDeleted_thenNotFoundAndNoRelease() {
        Long userId = 7L;
        Qr existing = qr(5L, userId, "link", Map.of("url", "https://example.com"));
        existing.setDeleted(true);

        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());

        assertThatThrownBy(() -> qrService.deleteQrByQrId(5L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("QR zaten silinmiş");

        verify(entitlementService, never()).release(userId, CatalogProducts.QR_CREATE, 1);
        verify(entitlementService, never()).release(userId, CatalogProducts.QR_MENU, 1);
        verify(entitlementService, never()).syncQrCreateEntitlements(userId);
        verify(qrRepository, never()).save(any(Qr.class));
    }

    @Test
    void deleteQrByQrId_whenActivePackageLinkQr_thenSyncQrCreateEntitlements() {
        Long userId = 7L;
        Qr existing = qr(6L, userId, "link", Map.of("url", "https://example.com"));
        existing.setPurchaseId(10L);

        when(qrRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());
        when(qrRepository.save(any(Qr.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRepository.findByQrIdAndDeletedFalse(6L)).thenReturn(Optional.empty());

        qrService.deleteQrByQrId(6L);

        verify(entitlementService).syncQrCreateEntitlements(userId);
        verify(entitlementService, never()).release(userId, CatalogProducts.QR_CREATE, 1);
        verify(entitlementService, never()).release(userId, CatalogProducts.QR_MENU, 1);
    }

    @Test
    void deleteQrByQrId_whenLegacyPackageQr_thenSyncQrCreateEntitlements() {
        Long userId = 7L;
        Qr existing = qr(8L, userId, "link", Map.of("url", "https://legacy.example.com"));
        existing.setPurchaseId(99L);

        when(qrRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());
        when(qrRepository.save(any(Qr.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRepository.findByQrIdAndDeletedFalse(8L)).thenReturn(Optional.empty());

        qrService.deleteQrByQrId(8L);

        verify(entitlementService).syncQrCreateEntitlements(userId);
        verify(entitlementService, never()).release(userId, CatalogProducts.QR_CREATE, 1);
    }

    @Test
    void getUserQrs_whenScopeCurrent_thenReturnOnlyActivePackageQrs() {
        Long userId = 7L;
        Qr currentQr = qr(1L, userId, "link", Map.of("url", "https://current.example.com"));
        currentQr.setPurchaseId(10L);
        Qr legacyQr = qr(2L, userId, "link", Map.of("url", "https://legacy.example.com"));
        legacyQr.setPurchaseId(99L);

        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());
        when(qrRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(currentQr, legacyQr));
        when(entitlementService.resolveActivePurchaseId(userId)).thenReturn(10L);
        when(purchaseRepository.findAllById(anyCollection())).thenReturn(List.of(
                com.ael.algoryqrservice.model.Purchase.builder().id(10L).packageName("Ultimate").build(),
                com.ael.algoryqrservice.model.Purchase.builder().id(99L).packageName("Pro").build()
        ));

        QrListPageResponse response = qrService.getUserQrs(userId, false, 0, 5, QrListScope.CURRENT);

        assertThat(response.getContent())
                .extracting(QrListResponse::getQrId)
                .containsExactly(1L);
        assertThat(response.getContent().getFirst().getActivePackage()).isTrue();
        assertThat(response.getContent().getFirst().getLegacy()).isFalse();
    }

    @Test
    void createQR_afterMenuSoftDeleted_whenNoActiveLiveMenu_thenNotBlockedByConflict() throws Exception {
        Long userId = 7L;
        QrRequest request = new QrRequest();
        request.setType("menu");
        request.setDetails(Map.of("branchId", 3L));
        QrResponse expected = QrResponse.builder().qrId(22L).build();

        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        when(entitlementService.consume(userId, CatalogProducts.QR_CREATE, 1)).thenReturn(new ConsumedEntitlement(10L, 2L, 1));
        when(qrProviderFactory.get(any(), eq(QrRequest.class))).thenReturn(qrProvider);
        when(qrProvider.createQr(request)).thenReturn(expected);

        QrResponse response = qrService.createQR(request, userId);

        assertThat(response.getQrId()).isEqualTo(22L);
        verify(entitlementService, never()).hasUsableQrCreatePackage(userId);
    }

    private Qr qr(Long qrId, Long userId, String type, Map<String, Object> details) {
        ObjectMapper mapper = new ObjectMapper();
        return Qr.builder()
                .qrId(qrId)
                .userId(userId)
                .qrName("QR " + qrId)
                .imgSrc("image-" + qrId)
                .qrType(QrType.builder().typeName(type).build())
                .details(mapper.valueToTree(details))
                .build();
    }
}
