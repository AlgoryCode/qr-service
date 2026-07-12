package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.QrCodeGeneratorUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class QrGenerationService {

    private final QrCodeGeneratorUtil qrCodeGeneratorUtil;
    private final QrRepository qrRepository;
    private final ObjectMapper objectMapper;

    public Qr createAndSave(QrRequest request, String content) throws WriterException, IOException {
        String base64 = qrCodeGeneratorUtil.generateBase64Png(content);

        Qr qr = Qr.builder()
                .userId(request.getUserId())
                .qrName(request.getQrName())
                .details(objectMapper.valueToTree(request.getDetails()))
                .imgSrc(base64)
                .build();

        return qrRepository.save(qr);
    }

    public QrResponse toResponse(Qr qr) {
        return QrResponse.builder()
                .qrId(qr.getQrId())
                .imgSrc(qr.getImgSrc())
                .build();
    }

    public Qr updateQrContent(Qr qr, String content) throws WriterException, IOException {
        String base64 = qrCodeGeneratorUtil.generateBase64Png(content);
        qr.setImgSrc(base64);
        return qrRepository.save(qr);
    }
}
