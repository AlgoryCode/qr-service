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
            String content = buildWifiContent(req.getDetails());
            return qrGenerationService.createAndSave(req, content);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate wifi QR", e);
        }
    }

    public String buildWifiContent(Map<String, Object> details) {
        String ssid = value(details.get("ssid"));
        String password = value(details.get("password"));
        String security = value(details.get("security"));

        return "WIFI:T:" + security + ";S:" + ssid + ";P:" + password + ";;";
    }

    private String value(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
