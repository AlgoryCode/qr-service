package com.ael.algoryqrservice.provider;

import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.google.zxing.WriterException;

import java.io.IOException;

public interface QrProvider<T extends QrRequest> {
    Type getType();
    Class<T> requestType();
    QrResponse createQr(T request) throws WriterException, IOException;

}
