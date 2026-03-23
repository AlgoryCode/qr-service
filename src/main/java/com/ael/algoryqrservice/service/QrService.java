package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.factory.QrProviderFactory;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.Type;
import com.ael.algoryqrservice.model.dto.QrNameRequest;
import com.ael.algoryqrservice.model.dto.QrNameResponse;
import com.ael.algoryqrservice.model.dto.QrListResponse;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.provider.QrProvider;
import com.ael.algoryqrservice.repository.QrRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.WriterException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    }

    public QrResponse updateQr(Long qrId, QrRequest req) throws IOException, WriterException {
        Qr existingQr = qrRepository.findById(qrId)
                .orElseThrow(() -> new EntityNotFoundException("QR bulunamadı: " + qrId));

        if (existingQr.isDeleted()) {
            throw new EntityNotFoundException("QR zaten silinmiş: " + qrId);
        }

        if (req.getType() == null || req.getType().isBlank()) {
            throw new IllegalArgumentException("type alanı zorunludur");
        }

        // Keep ownership unchanged unless the same user id is sent.
        if (req.getUserId() != null && !req.getUserId().equals(existingQr.getUserId())) {
            throw new IllegalArgumentException("QR başka bir kullanıcıya ait, userId değiştirilemez");
        }

        req.setUserId(existingQr.getUserId());

        existingQr.setDeleted(true);
        qrRepository.save(existingQr);

        return createQR(req);
    }

    public List<QrListResponse> getUserQrs(Long userId) {
        return qrRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToListResponse)
                .toList();
    }

    public QrNameResponse updateQrName(Long qrId, QrNameRequest req) {
        if (req == null || req.getQrName() == null || req.getQrName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qrName zorunludur");
        }

        Qr existingQr = qrRepository.findById(qrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR bulunamadı: " + qrId));

        if (existingQr.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR zaten silinmiş: " + qrId);
        }

        existingQr.setQrName(req.getQrName());
        qrRepository.save(existingQr);

        return QrNameResponse.builder()
                .qrId(existingQr.getQrId())
                .qrName(existingQr.getQrName())
                .build();
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

    public void deleteQrByQrId(Long qrId){
        Qr qr = qrRepository.findById(qrId)
                .orElseThrow(() -> new EntityNotFoundException("QR bulunamadı: " + qrId));
        qr.setDeleted(true);
        qrRepository.save(qr);
    }

}
