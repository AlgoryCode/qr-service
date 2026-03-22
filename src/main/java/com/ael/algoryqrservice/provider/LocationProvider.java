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
public class LocationProvider implements QrProvider {
    private final QrGenerationService qrGenerationService;

    @Override
    public Type getType() {
        return Type.LOCATION;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) {
        try {
            String content = buildLocationContent(request.getDetails());
            return qrGenerationService.createAndSave(request, content);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate location QR", e);
        }
    }

    public String buildLocationContent(Map<String, Object> details) {
        String latitude = value(details.get("latitude"));
        String longitude = value(details.get("longitude"));
        String label = value(details.get("label"));

        if (!label.isBlank()) {
            return "geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude + "(" + label + ")";
        }

        return "geo:" + latitude + "," + longitude;
    }

    private String value(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
