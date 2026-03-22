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
public class ContactProvider implements QrProvider {
    private final QrGenerationService qrGenerationService;

    @Override
    public Type getType() {
        return Type.CONTACT;
    }

    @Override
    public Class<QrRequest>  requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) {
        try {
            String content = buildContactContent(request.getDetails());
            return qrGenerationService.createAndSave(request, content);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate contact QR", e);
        }
    }

    public String buildContactContent(Map<String, Object> details) {
        String fullName = value(details.get("fullName"));
        String phone = value(details.get("phone"));
        String email = value(details.get("email"));
        String company = value(details.get("company"));
        String title = value(details.get("title"));

        return "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "FN:" + fullName + "\n" +
                "TEL:" + phone + "\n" +
                "EMAIL:" + email + "\n" +
                "ORG:" + company + "\n" +
                "TITLE:" + title + "\n" +
                "END:VCARD";
    }

    private String value(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
