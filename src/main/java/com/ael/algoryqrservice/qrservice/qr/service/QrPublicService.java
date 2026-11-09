package com.ael.algoryqrservice.qrservice.qr.service;

import com.ael.algoryqrservice.qrservice.model.Type;
import com.ael.algoryqrservice.qrservice.qr.dto.QrPublicResponse;
import com.ael.algoryqrservice.qrservice.qr.model.Qr;
import com.ael.algoryqrservice.qrservice.qr.repository.QrRepository;
import com.ael.algoryqrservice.qrservice.qr.repository.QrScanStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrPublicService {

    static final ZoneId SCAN_ZONE = ZoneId.of("Europe/Istanbul");

    private final QrRepository qrRepository;
    private final QrScanStore qrScanStore;
    private final ObjectMapper objectMapper;

    public QrPublicResponse resolve(String code, boolean track) {
        if (!QrPublicCodes.isWellFormed(code)) {
            throw notFound();
        }
        Qr qr = qrRepository.findByPublicCodeAndDeletedFalse(code).filter(Qr::isActive).orElseThrow(this::notFound);
        Type type = QrPayloads.inferType(qr.getDetails());
        if (type == null) {
            throw notFound();
        }
        if (track && qr.isTrackingEnabled()) {
            recordScan(qr);
        }
        Map<String, Object> details = objectMapper.convertValue(qr.getDetails(), new TypeReference<>() {});
        return new QrPublicResponse(type.getValue(), qr.getQrName(), QrPayloads.content(type, details), details);
    }

    private void recordScan(Qr qr) {
        try {
            qrScanStore.increment(qr.getQrId(), LocalDate.now(SCAN_ZONE));
        } catch (DataAccessException ex) {
            // A lost count must never stop the customer from reaching the content.
            log.warn("QR scan could not be recorded for qrId={}", qr.getQrId(), ex);
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "QR bulunamadı");
    }
}
