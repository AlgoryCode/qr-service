package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.util.QrCodeGeneratorUtil;
import com.google.zxing.WriterException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** The QR printed on a device: its public status page link and the PNG to print. */
@Service
public class DeviceQrService {

    private final ConsoleCatalogService catalogService;
    private final QrCodeGeneratorUtil qrCodeGenerator;
    private final String publicBaseUrl;

    public DeviceQrService(
            ConsoleCatalogService catalogService,
            QrCodeGeneratorUtil qrCodeGenerator,
            @Value("${app.qr.public-base-url:https://qr.algorycode.com}") String publicBaseUrl
    ) {
        this.catalogService = catalogService;
        this.qrCodeGenerator = qrCodeGenerator;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public ConsoleDtos.DeviceQrResponse qr(Long consoleId) {
        GameConsole console = catalogService.requireOwned(consoleId);
        String url = link(publicBaseUrl, console.getPublicToken());
        try {
            return new ConsoleDtos.DeviceQrResponse(console.getId(), console.getName(), url, qrCodeGenerator.generateBase64Png(url));
        } catch (WriterException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "QR kod oluşturulamadı");
        }
    }

    static String link(String baseUrl, String publicToken) {
        return baseUrl + "/cihaz/" + URLEncoder.encode(publicToken, StandardCharsets.UTF_8);
    }
}
