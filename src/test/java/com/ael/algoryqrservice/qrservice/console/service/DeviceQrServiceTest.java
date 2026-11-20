package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.util.QrCodeGeneratorUtil;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceQrServiceTest {

    @Test
    void qrPointsAtTheDevicePageAndIsAPng() {
        ConsoleCatalogService catalog = mock(ConsoleCatalogService.class);
        GameConsole console = new GameConsole();
        console.setId(7L);
        console.setName("PC 1");
        console.setPublicToken("abcDEF123");
        when(catalog.requireOwned(7L)).thenReturn(console);

        DeviceQrService service = new DeviceQrService(catalog, new QrCodeGeneratorUtil(), "https://qr.example.com/");
        ConsoleDtos.DeviceQrResponse qr = service.qr(7L);

        assertThat(qr.url()).isEqualTo("https://qr.example.com/cihaz/abcDEF123");
        assertThat(qr.consoleName()).isEqualTo("PC 1");
        byte[] png = Base64.getDecoder().decode(qr.imageBase64());
        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }
}
