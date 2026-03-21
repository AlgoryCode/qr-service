package com.ael.algoryqrservice.provider;

import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.google.zxing.WriterException;

import java.io.IOException;
import java.util.Map;

public class TextProvider implements QrProvider{
    @Override
    public Type getType() {
        return Type.TEXT;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) throws WriterException, IOException {
        return null;
    }

    public String buildTextContent(Map<String, Object> details) {
        return value(details.get("text"));
    }

    private String value(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
