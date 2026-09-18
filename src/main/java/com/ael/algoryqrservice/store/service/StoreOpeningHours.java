package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.MerchantStatus;
import com.ael.algoryqrservice.store.model.StoreWorkingHour;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class StoreOpeningHours {

    public boolean isOpenAt(Merchant merchant, LocalDateTime moment) {
        if (merchant.getStatus() != MerchantStatus.ACTIVE || merchant.isManuallyClosed()) {
            return false;
        }
        List<StoreWorkingHour> hours = merchant.getWorkingHours();
        if (hours == null || hours.isEmpty()) {
            return true;
        }
        return hours.stream()
                .filter(hour -> hour.getDay() == moment.getDayOfWeek())
                .anyMatch(hour -> covers(hour, moment.toLocalTime()));
    }

    /** A window whose closing time is not after its opening time runs past midnight. */
    private boolean covers(StoreWorkingHour hour, LocalTime time) {
        if (hour.isClosed() || hour.getOpensAt() == null || hour.getClosesAt() == null) {
            return false;
        }
        LocalTime opensAt = hour.getOpensAt();
        LocalTime closesAt = hour.getClosesAt();
        if (closesAt.isAfter(opensAt)) {
            return !time.isBefore(opensAt) && time.isBefore(closesAt);
        }
        return !time.isBefore(opensAt) || time.isBefore(closesAt);
    }
}
