package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.model.QrType;
import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.provider.QrProvider;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AuthProvider;

@Service
@RequiredArgsConstructor
public class QrService {


    private final QrProviderFactory qrProviderFactory;

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

}
