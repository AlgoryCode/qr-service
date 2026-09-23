package com.ael.algoryqrservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class StockQuantities {

    private static final int SCALE = 3;

    private StockQuantities() {
    }

    static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
