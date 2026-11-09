package com.ael.algoryqrservice.qrservice.provider;

import com.ael.algoryqrservice.qrservice.model.Type;
import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.qr.dto.QrResponse;
import com.ael.algoryqrservice.qrservice.qr.service.QrGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailProvider implements QrProvider<QrRequest> {
    private final QrGenerationService qrGenerationService;

    @Override
    public Type getType() {
        return Type.MAIL;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) {
        try {
            return qrGenerationService.toResponse(qrGenerationService.createForType(request, getType()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate mail QR", e);
        }
    }
}
