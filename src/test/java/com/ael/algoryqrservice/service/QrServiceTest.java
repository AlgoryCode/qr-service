package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.catalog.CatalogScopes;
import com.ael.algoryqrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.QrType;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.QrListResponse;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.provider.QrProvider;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private QrProvider<QrRequest> qrProvider;

    @InjectMocks
    private QrService qrService;

    @Test
    void getUserQrs_whenMenuIsPassiveOrDeleted_thenHideMenuQr() {
        Long userId = 7L;
        Qr activeMenuQr = qr(1L, userId, "menu", Map.of("themeId", "classic", "businessName", "Aktif"));
        Qr hiddenMenuQr = qr(2L, userId, "menu", Map.of("themeId", "classic", "businessName", "Pasif"));
        Qr linkQr = qr(3L, userId, "link", Map.of("url", "https://example.com"));

        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());
        when(qrRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(activeMenuQr, hiddenMenuQr, linkQr));
        when(menuRepository.findActiveQrIdsByUserIdAndQrIdIn(eq(userId), anyCollection()))
                .thenReturn(Set.of(activeMenuQr.getQrId()));

        List<QrListResponse> response = qrService.getUserQrs(userId);

        assertThat(response)
                .extracting(QrListResponse::getQrId)
                .containsExactly(activeMenuQr.getQrId(), linkQr.getQrId());
    }

    @Test
    void createQR_whenActiveMenuExistsAndPackageUsable_thenConflict() {
        Long userId = 7L;
        QrRequest request = new QrRequest();
        request.setType("menu");

        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        when(menuRepository.existsActiveLiveMenuQrForUser(userId)).thenReturn(true);
        when(entitlementService.hasUsableQrMenuPackage(userId)).thenReturn(true);

        assertThatThrownBy(() -> qrService.createQR(request, userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusException = (ResponseStatusException) ex;
                    assertThat(statusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(statusException.getReason()).contains("Aktif bir dijital menü");
                });

        verify(entitlementService, never()).consume(eq(userId), eq(CatalogProducts.QR_MENU), eq(1));
        verify(entitlementService, never()).consume(eq(userId), eq(CatalogProducts.QR_CREATE), eq(1));
    }

    @Test
    void createQR_whenNoActiveMenu_thenCreateProceeds() throws Exception {
        Long userId = 7L;
        QrRequest request = new QrRequest();
        request.setType("menu");
        QrResponse expected = QrResponse.builder().qrId(11L).build();

        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        when(menuRepository.existsActiveLiveMenuQrForUser(userId)).thenReturn(false);
        doNothing().when(entitlementService).consume(userId, CatalogProducts.QR_MENU, 1);
        doNothing().when(entitlementService).consume(userId, CatalogProducts.QR_CREATE, 1);
        when(qrProviderFactory.get(any(), eq(QrRequest.class))).thenReturn(qrProvider);
        when(qrProvider.createQr(request)).thenReturn(expected);

        QrResponse response = qrService.createQR(request, userId);

        assertThat(response.getQrId()).isEqualTo(11L);
        verify(entitlementService).consume(userId, CatalogProducts.QR_MENU, 1);
        verify(entitlementService).consume(userId, CatalogProducts.QR_CREATE, 1);
    }

    @Test
    void deleteQrByQrId_whenMenuLinked_thenSoftDeleteMenu() {
        Long userId = 7L;
        Qr existing = qr(5L, userId, "menu", Map.of("themeId", "classic", "businessName", "Kafe"));
        Menu menu = Menu.builder()
                .menuId(9L)
                .qrId(5L)
                .userId(userId)
                .themeId("classic")
                .businessName("Kafe")
                .active(true)
                .build();

        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(userId).build());
        when(qrRepository.save(any(Qr.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuRepository.findByQrIdAndDeletedFalse(5L)).thenReturn(Optional.of(menu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

        qrService.deleteQrByQrId(5L);

        ArgumentCaptor<Menu> menuCaptor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository).save(menuCaptor.capture());
        assertThat(menuCaptor.getValue().isDeleted()).isTrue();
        assertThat(menuCaptor.getValue().isActive()).isFalse();
        assertThat(existing.isDeleted()).isTrue();
    }

    @Test
    void createQR_afterMenuSoftDeleted_whenNoActiveLiveMenu_thenNotBlockedByConflict() throws Exception {
        Long userId = 7L;
        QrRequest request = new QrRequest();
        request.setType("menu");
        QrResponse expected = QrResponse.builder().qrId(22L).build();

        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_CREATE_OWNER);
        doNothing().when(entitlementService).requireScope(userId, CatalogScopes.QR_MENU_OWNER);
        when(menuRepository.existsActiveLiveMenuQrForUser(userId)).thenReturn(false);
        doNothing().when(entitlementService).consume(userId, CatalogProducts.QR_MENU, 1);
        doNothing().when(entitlementService).consume(userId, CatalogProducts.QR_CREATE, 1);
        when(qrProviderFactory.get(any(), eq(QrRequest.class))).thenReturn(qrProvider);
        when(qrProvider.createQr(request)).thenReturn(expected);

        QrResponse response = qrService.createQR(request, userId);

        assertThat(response.getQrId()).isEqualTo(22L);
        verify(menuRepository).existsActiveLiveMenuQrForUser(userId);
        verify(entitlementService, never()).hasUsableQrMenuPackage(userId);
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
