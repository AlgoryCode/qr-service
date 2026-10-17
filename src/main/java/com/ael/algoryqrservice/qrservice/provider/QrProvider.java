package com.ael.algoryqrservice.qrservice.provider;

import com.ael.algoryqrservice.qrservice.model.Type;
import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.qr.dto.QrResponse;
import com.google.zxing.WriterException;

import java.io.IOException;

public interface QrProvider<T extends QrRequest> {
    Type getType();
    Class<T> requestType();
    QrResponse createQr(T request) throws WriterException, IOException;

}
