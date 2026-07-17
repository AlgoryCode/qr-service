package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.QrType;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.QrListResponse;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
