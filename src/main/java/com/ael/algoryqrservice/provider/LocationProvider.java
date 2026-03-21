package com.ael.algoryqrservice.provider;

import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.google.zxing.WriterException;

import java.io.IOException;
import java.util.Map;

public class LocationProvider implements QrProvider {
    @Override
    public Type getType() {
        return Type.LOCATION;
    }

    @Override
    public Class<QrRequest> requestType() {
        return QrRequest.class;
    }

    @Override
    public QrResponse createQr(QrRequest request) throws WriterException, IOException {
        return null;
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
