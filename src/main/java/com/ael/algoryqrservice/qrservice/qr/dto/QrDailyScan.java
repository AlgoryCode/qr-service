package com.ael.algoryqrservice.qrservice.qr.dto;

import java.time.LocalDate;

public record QrDailyScan(LocalDate date, long count) {
}
