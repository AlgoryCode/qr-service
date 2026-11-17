package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.CatalogSchedule;
import com.ael.algoryqrservice.qrservice.model.CatalogSchedule.TargetType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogScheduleRulesTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 17, 0, 0);

    @Test
    void campaignWindowsOverlap_permanentChangesOccupyTheirStart() {
        CatalogSchedule campaign = CatalogSchedule.builder().startsAt(T).endsAt(T.plusDays(3)).build();
        assertThat(campaign.overlaps(T.plusDays(1), null)).isTrue();          // price change inside the window
        assertThat(campaign.overlaps(T.plusDays(3), T.plusDays(5))).isTrue(); // touching end
        assertThat(campaign.overlaps(T.plusDays(4), null)).isFalse();
        assertThat(campaign.overlaps(T.minusDays(2), T.minusDays(1))).isFalse();

        CatalogSchedule priceChange = CatalogSchedule.builder().startsAt(T).build();
        assertThat(priceChange.overlaps(T.minusDays(1), T.plusDays(1))).isTrue();
        assertThat(priceChange.overlaps(T.plusDays(1), null)).isFalse();
    }

    @Test
    void validate_requiresTheRightFieldsAndAForwardWindow() {
        assertThatThrownBy(() -> CatalogScheduleService.validate(req(TargetType.PRODUCT, null, null, T, null), T))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("birim fiyat");
        assertThatThrownBy(() -> CatalogScheduleService.validate(req(TargetType.PACKAGE, null, null, T, T.plusDays(1)), T))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("indirim");
        assertThatThrownBy(() -> CatalogScheduleService.validate(req(TargetType.PACKAGE, null, BigDecimal.TEN, T, T), T))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Bitiş");
        CatalogScheduleService.validate(req(TargetType.PRODUCT, BigDecimal.ONE, null, T.plusDays(1), null), T);
    }

    private static CatalogScheduleService.Request req(TargetType type, BigDecimal unitPrice, BigDecimal monthly,
                                                      LocalDateTime start, LocalDateTime end) {
        return new CatalogScheduleService.Request(type, 1L, "x", unitPrice, monthly, null, start, end);
    }
}
