package com.ael.algoryqrservice.qrservice.provider;

import com.ael.algoryqrservice.qrservice.model.Type;
import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.qr.dto.QrResponse;
import com.ael.algoryqrservice.qrservice.qr.service.QrGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WifiProvider implements QrProvider<QrRequest> {

    private final QrGenerationService qrGenerationService;

    @Override
    public Type getType() {
        return Type.WIFI;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest req) {
        try {
            return qrGenerationService.toResponse(qrGenerationService.createForType(req, getType()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate wifi QR", e);
        }
    }
}
