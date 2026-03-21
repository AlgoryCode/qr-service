package com.ael.algoryqrservice.provider;

import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.ContactRequest;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.google.zxing.WriterException;

import java.io.IOException;
import java.util.Map;

public class ContactProvider implements QrProvider {
    @Override
    public Type getType() {
        return Type.CONTACT;
    }

    @Override
    public Class<QrRequest>  requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) throws WriterException, IOException {
        return null;
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
