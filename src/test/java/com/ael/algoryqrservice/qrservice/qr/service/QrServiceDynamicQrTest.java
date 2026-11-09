package com.ael.algoryqrservice.qrservice.qr.service;

import com.ael.algoryqrservice.qrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.qrservice.model.Type;
import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.qr.dto.QrResponse;
import com.ael.algoryqrservice.qrservice.qr.model.Qr;
import com.ael.algoryqrservice.qrservice.qr.repository.QrRepository;
import com.ael.algoryqrservice.qrservice.qr.repository.QrScanStore;
import com.ael.algoryqrservice.qrservice.security.ProductUsageGateway;
import com.ael.algoryqrservice.qrservice.util.QrCodeGeneratorUtil;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrServiceDynamicQrTest {

    private static final Long MERCHANT = 7L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private QrProviderFactory qrProviderFactory;
    @Mock private QrRepository qrRepository;
    @Mock private ProductUsageGateway productUsageGateway;
    @Mock private SecurityUtils securityUtils;
    @Mock private QrScanStore qrScanStore;
    @Mock private QrCodeGeneratorUtil qrCodeGeneratorUtil;

    private QrService qrService;

    @BeforeEach
    void setUp() throws Exception {
        QrPublicCodes codes = new QrPublicCodes(new SecureRandom(), qrRepository, "https://qr.example.com/");
        QrGenerationService generation = new QrGenerationService(qrCodeGeneratorUtil, qrRepository, objectMapper, codes);
        qrService = new QrService(
                qrProviderFactory, generation, codes, qrScanStore, qrRepository, null, null, objectMapper,
                null, productUsageGateway, null, null, null, securityUtils);
        lenient().when(securityUtils.getCurrentMerchantId()).thenReturn(MERCHANT);
        lenient().when(qrRepository.save(any(Qr.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(qrCodeGeneratorUtil.generateBase64Png(anyString())).thenAnswer(call -> "img:" + call.getArgument(0));
    }

    private Qr qr(String code, Map<String, Object> details) {
        return Qr.builder().qrId(5L).merchantId(MERCHANT).qrName("Eski").publicCode(code).imgSrc("printed")
                .details(objectMapper.valueToTree(details)).build();
    }

    private QrRequest request(String type, Map<String, Object> details) {
        QrRequest request = new QrRequest();
        request.setQrName("Yeni");
        request.setType(type);
        request.setDetails(details);
        return request;
    }

    @Test
    void editingKeepsTheSameRowAddressAndImageWithoutSpendingARight() throws Exception {
        Qr existing = qr("k7m2xq9fah", Map.of("url", "https://old.example.com"));
        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));

        QrResponse response = qrService.updateQr(5L, request("link", Map.of("url", "new.example.com")));

        assertThat(response.getQrId()).isEqualTo(5L);
        assertThat(response.getImgSrc()).isEqualTo("printed");
        assertThat(response.getPublicUrl()).isEqualTo("https://qr.example.com/qr/k7m2xq9fah");
        assertThat(existing.isDeleted()).isFalse();
        assertThat(existing.getDetails().get("url").asText()).isEqualTo("https://new.example.com");
        verifyNoInteractions(productUsageGateway);
        verify(qrCodeGeneratorUtil, never()).generateBase64Png(anyString());
    }

    @Test
    void editingAStaticQrRebuildsItsImageAndStaysStatic() throws Exception {
        Qr existing = qr(null, Map.of("url", "https://old.example.com"));
        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));

        QrResponse response = qrService.updateQr(5L, request("link", Map.of("url", "https://new.example.com")));

        assertThat(existing.getPublicCode()).isNull();
        assertThat(response.getImgSrc()).isEqualTo("img:https://new.example.com");
        assertThat(response.getPublicUrl()).isNull();
    }

    @Test
    void createHonoursTheChosenMode() throws Exception {
        QrPublicCodes codes = new QrPublicCodes(new SecureRandom(), qrRepository, "https://qr.example.com");
        QrGenerationService generation = new QrGenerationService(qrCodeGeneratorUtil, qrRepository, objectMapper, codes);
        QrRequest dynamicWifi = request("wifi", Map.of("ssid", "Cafe", "security", "NONE"));
        dynamicWifi.setQrMode("dynamic");
        QrRequest staticLink = request("link", Map.of("url", "https://a.example.com"));
        staticLink.setQrMode("static");

        Qr wifi = generation.createForType(dynamicWifi, Type.WIFI);
        Qr link = generation.createForType(staticLink, Type.LINK);

        assertThat(wifi.getPublicCode()).hasSize(10);
        assertThat(wifi.getImgSrc()).startsWith("img:https://qr.example.com/qr/");
        assertThat(link.getPublicCode()).isNull();
        assertThat(link.getImgSrc()).isEqualTo("img:https://a.example.com");
    }

    @Test
    void editingWifiRebuildsTheImage() throws Exception {
        Qr existing = qr(null, Map.of("ssid", "Eski", "password", "12345678", "security", "WPA"));
        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));

        QrResponse response = qrService.updateQr(5L,
                request("wifi", Map.of("ssid", "Yeni", "password", "87654321", "security", "WPA")));

        assertThat(response.getImgSrc()).isEqualTo("img:WIFI:T:WPA;S:Yeni;P:87654321;;");
        assertThat(existing.getPublicCode()).isNull();
    }

    @Test
    void theTypeCannotChange() {
        when(qrRepository.findById(5L)).thenReturn(Optional.of(qr("k7m2xq9fah", Map.of("url", "https://a.example.com"))));

        assertThatThrownBy(() -> qrService.updateQr(5L, request("text", Map.of("text", "x"))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void anInvalidEditLeavesTheQrUntouched() {
        Qr existing = qr("k7m2xq9fah", Map.of("url", "https://old.example.com"));
        when(qrRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> qrService.updateQr(5L, request("link", Map.of("url", ""))))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(existing.getDetails().get("url").asText()).isEqualTo("https://old.example.com");
        assertThat(existing.isDeleted()).isFalse();
        verify(qrRepository, never()).save(any());
    }

    @Test
    void aRejectedCreateSpendsNoRight() {
        assertThatThrownBy(() -> qrService.createQR(request(Type.LINK.getValue(), Map.of("url", "")), MERCHANT))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(productUsageGateway);
    }
}
