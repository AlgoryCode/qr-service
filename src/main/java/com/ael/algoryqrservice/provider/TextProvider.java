package com.ael.algoryqrservice.provider;

import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.service.QrGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TextProvider implements QrProvider{
    private final QrGenerationService qrGenerationService;

    @Override
    public Type getType() {
        return Type.TEXT;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) {
        try {
            String content = buildTextContent(request.getDetails());
            return qrGenerationService.createAndSave(request, content);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate text QR", e);
        }
    }

    public String buildTextContent(Map<String, Object> details) {
        return value(details.get("text"));
    }

    private String value(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
