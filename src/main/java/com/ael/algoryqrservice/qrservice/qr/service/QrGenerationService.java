package com.ael.algoryqrservice.qrservice.qr.service;

import com.ael.algoryqrservice.qrservice.model.Type;
import com.ael.algoryqrservice.qrservice.qr.model.Qr;
import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.qr.dto.QrResponse;
import com.ael.algoryqrservice.qrservice.qr.repository.QrRepository;
import com.ael.algoryqrservice.qrservice.util.QrCodeGeneratorUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QrGenerationService {

    private final QrCodeGeneratorUtil qrCodeGeneratorUtil;
    private final QrRepository qrRepository;
    private final ObjectMapper objectMapper;
    private final QrPublicCodes qrPublicCodes;

    public Qr createAndSave(QrRequest request, String content) throws WriterException, IOException {
        String base64 = qrCodeGeneratorUtil.generateBase64Png(content);

        Qr qr = Qr.builder()
                .merchantId(request.getMerchantId())
                .purchaseId(request.getPurchaseId())
                .qrName(request.getQrName())
                .details(objectMapper.valueToTree(request.getDetails()))
                .imgSrc(base64)
                .build();

        return qrRepository.save(qr);
    }

    public Qr createForType(QrRequest request, Type type) throws WriterException, IOException {
        if (QrPayloads.isDynamic(request.getQrMode(), type)) {
            return createDynamic(request);
        }
        return createAndSave(request, QrPayloads.content(type, request.getDetails()));
    }

    /** Creates a QR whose image holds only its permanent address; the content is looked up on every scan. */
    public Qr createDynamic(QrRequest request) throws WriterException, IOException {
        String code = qrPublicCodes.generateUnique();
        String base64 = qrCodeGeneratorUtil.generateBase64Png(qrPublicCodes.urlFor(code));

        Qr qr = Qr.builder()
                .merchantId(request.getMerchantId())
                .purchaseId(request.getPurchaseId())
                .qrName(request.getQrName())
                .details(objectMapper.valueToTree(request.getDetails()))
                .imgSrc(base64)
                .publicCode(code)
                .trackingEnabled(!Boolean.FALSE.equals(request.getTrackingEnabled()))
                .build();

        return qrRepository.save(qr);
    }

    /** Changes a QR's content on the same row. A dynamic QR keeps its image; a static QR needs a new one. */
    public Qr updateContent(Qr qr, Type type, String name, Map<String, Object> details)
            throws WriterException, IOException {
        qr.setQrName(name);
        qr.setDetails(objectMapper.valueToTree(details));
        if (qr.getPublicCode() == null) {
            qr.setImgSrc(qrCodeGeneratorUtil.generateBase64Png(QrPayloads.content(type, details)));
        }
        return qrRepository.save(qr);
    }

    public QrResponse toResponse(Qr qr) {
        return QrResponse.builder()
                .qrId(qr.getQrId())
                .imgSrc(qr.getImgSrc())
                .publicUrl(qrPublicCodes.urlFor(qr.getPublicCode()))
                .build();
    }

    public Qr updateQrContent(Qr qr, String content) throws WriterException, IOException {
        String base64 = qrCodeGeneratorUtil.generateBase64Png(content);
        qr.setImgSrc(base64);
        return qrRepository.save(qr);
    }
}
