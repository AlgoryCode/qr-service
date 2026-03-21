package com.ael.algoryqrservice.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.repository.QrRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WifiProvider implements QrProvider<QrRequest> {

    private final QrRepository qrRepository;
    private final ObjectMapper objectMapper;

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
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter
                    .encode(content, BarcodeFormat.QR_CODE, 300, 300);


            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            qrRepository.save(Qr.builder()
                    .qrName(req.getQrName())
                    .details(objectMapper.valueToTree(req.getDetails()))
                    .imgSrc(base64)
                    .build());

            return QrResponse.builder().imgSrc(base64).build();

        } catch (Exception e) {
            System.out.println(e);
        }
        return null;
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
