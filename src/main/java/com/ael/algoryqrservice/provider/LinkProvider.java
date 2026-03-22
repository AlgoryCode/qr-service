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
public class LinkProvider implements QrProvider<QrRequest> {
    private final QrGenerationService qrGenerationService;

    @Override
    public Type getType() {
        return Type.LINK;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) {
        try {
            String content = buildLinkContent(request.getDetails());
            return qrGenerationService.createAndSave(request, content);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate link QR", e);
        }
    }

    public String buildLinkContent(Map<String, Object> details) {
        String url = value(details.get("url"));

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        return url;
    }

    private String value(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
