package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrListResponse;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.provider.QrProvider;
import com.ael.algoryqrservice.repository.QrRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QrService {


    private final QrProviderFactory qrProviderFactory;
    private final QrRepository qrRepository;
    private final ObjectMapper objectMapper;

    public <T extends QrRequest> QrResponse createQR(T req) throws IOException, WriterException {
        Type qrType = Type.from(req.getType());
        QrProvider<T> provider = qrProviderFactory.get(qrType,(Class<T>) req.getClass());
        return provider.createQr(req);

//        //TEXT , WİDTH , HEIGHT
//        try{
//            QRCodeWriter qrCodeWriter = new QRCodeWriter();
//            BitMatrix bitMatrix = qrCodeWriter
//                    .encode(req.getQrName(), BarcodeFormat.QR_CODE, 1, 2);
//
//
//            String projectPath = System.getProperty("user.dir");
//            Path dir = Paths.get(projectPath);
//
//            String fileName = "qrcode.png";
//            Path filePath = dir.resolve(fileName);
//
//
//            MatrixToImageWriter .writeToPath(bitMatrix,"PNG",filePath);
//
//
//        }catch (Exception e){
//            System.out.println(e);
//        }


    }

    public List<QrListResponse> getUserQrs(Long userId) {
        return qrRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToListResponse)
                .toList();
    }

    private QrListResponse mapToListResponse(Qr qr) {
        return QrListResponse.builder()
                .qrId(qr.getQrId())
                .userId(qr.getUserId())
                .qrName(qr.getQrName())
                .imgSrc(qr.getImgSrc())
                .details(objectMapper.convertValue(qr.getDetails(), new TypeReference<Map<String, Object>>() {}))
                .createdAt(qr.getCreatedAt())
                .build();
    }

}
