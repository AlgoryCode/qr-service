package com.ael.algoryqrservice.provider;

import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.google.zxing.WriterException;

import java.io.IOException;
import java.util.Map;

public class MailProvider implements QrProvider{
    @Override
    public Type getType() {
        return Type.MAIL;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) throws WriterException, IOException {
        return null;
    }

    public String buildMailContent(Map<String, Object> details) {
        String email = value(details.get("email"));
        String subject = value(details.get("subject"));
        String body = value(details.get("body"));

        String content = "mailto:" + email;

        boolean hasSubject = !subject.isBlank();
        boolean hasBody = !body.isBlank();

        if (hasSubject || hasBody) {
            content += "?";
            if (hasSubject) {
                content += "subject=" + subject;
            }
            if (hasBody) {
                if (hasSubject) {
                    content += "&";
                }
                content += "body=" + body;
            }
        }

        return content;
    }

    private String value(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
