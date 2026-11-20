package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import com.ael.algoryqrservice.qrservice.model.DeviceTier;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomerTxn;
import com.ael.algoryqrservice.qrservice.model.DeviceReservation;
import com.ael.algoryqrservice.qrservice.model.DeviceSessionItem;
import com.ael.algoryqrservice.qrservice.model.DeviceTariffRule;

import java.util.ArrayList;
import java.util.List;

final class DeviceMappers {

    private DeviceMappers() {
    }

    static ConsoleDtos.ReservationResponse reservation(DeviceReservation r, String consoleName) {
        return new ConsoleDtos.ReservationResponse(
                r.getId(),
                r.getBranchId(),
                r.getConsoleId(),
                consoleName,
                r.getCustomerId(),
                r.getCustomerName(),
                r.getPhone(),
                r.getNote(),
                r.getStartsAt(),
                r.getEndsAt(),
                r.getStatus(),
                r.getSessionId()
        );
    }

    static ConsoleDtos.TariffResponse tariff(DeviceTariffRule rule) {
        return new ConsoleDtos.TariffResponse(
                rule.getId(),
                rule.getBranchId(),
                rule.getName(),
                rule.getDeviceType(),
                days(rule.getDaysMask()),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.getAdjustPercent()
        );
    }

    /** ISO day numbers (1 = Monday) to a bit mask with bit 0 = Monday. */
    static int daysMask(List<Integer> days) {
        int mask = 0;
        for (Integer day : days) {
            if (day != null && day >= 1 && day <= 7) {
                mask |= 1 << (day - 1);
            }
        }
        return mask;
    }

    static List<Integer> days(int mask) {
        List<Integer> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            if ((mask & (1 << (day - 1))) != 0) {
                days.add(day);
            }
        }
        return days;
    }

    static ConsoleDtos.CustomerResponse customer(DeviceCustomer c) {
        return customer(c, null, 0);
    }

    static ConsoleDtos.CustomerResponse customer(DeviceCustomer c, DeviceTier tier, int membershipMinutes) {
        return new ConsoleDtos.CustomerResponse(
                c.getId(),
                c.getName(),
                c.getPhone(),
                c.getNote(),
                c.getBalance(),
                c.getFreeMinutes(),
                c.getLoyaltyProgressMinutes(),
                c.getTotalMinutes(),
                c.getTotalSpent(),
                c.getBonusBalance(),
                c.getReferralCode(),
                c.getReferredById(),
                c.getVisits(),
                c.getLastVisitAt(),
                tier == null ? null : tier.getName(),
                tier == null ? null : tier.getDiscountPercent(),
                membershipMinutes
        );
    }

    static ConsoleDtos.CustomerTxnResponse txn(DeviceCustomerTxn t) {
        return new ConsoleDtos.CustomerTxnResponse(
                t.getId(),
                t.getTxnType(),
                t.getAmount(),
                t.getPaymentMethod(),
                t.getSessionId(),
                t.getNote(),
                t.getCreatedAt()
        );
    }

    static ConsoleDtos.SessionItemResponse item(DeviceSessionItem i) {
        return new ConsoleDtos.SessionItemResponse(
                i.getId(),
                i.getMenuProductId(),
                i.getName(),
                i.getUnitPrice(),
                i.getQuantity(),
                i.getLineTotal()
        );
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final String TOKEN_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** Unguessable key for a public link. */
    static String newToken(int length) {
        return random(TOKEN_CHARS, length);
    }

    /** Short code people can read out, without look-alike characters. */
    static String newCode(int length) {
        return random(CODE_CHARS, length);
    }

    private static String random(String alphabet, int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return out.toString();
    }

    static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** Digits and a leading +, so "0532 111 22 33" and "05321112233" are the same customer. */
    static String normalizePhone(String phone) {
        String trimmed = blankToNull(phone);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9+]", "");
        return digits.isEmpty() ? null : digits;
    }
}
