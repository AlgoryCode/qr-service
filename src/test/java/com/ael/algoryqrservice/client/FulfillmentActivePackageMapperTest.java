package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentActivePackageMapperTest {

    @Test
    void toSummary_whenFulfillmentPackageIsActive_thenMarksItUsable() {
        ExternalActivePackageResponse source = new ExternalActivePackageResponse(
                9L,
                22L,
                17L,
                371L,
                3L,
                "ULTIMATE_PACKAGE",
                "Ultimate",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(30),
                "ACTIVE",
                Instant.parse("2026-09-24T20:11:24Z"),
                Instant.parse("2026-09-24T20:11:24Z"),
                List.of("QR_MENU"),
                List.of("QR_MENU_OWNER")
        );

        var summary = new FulfillmentActivePackageMapper().toSummary(source);

        assertThat(summary.getPackageCode()).isEqualTo("ULTIMATE_PACKAGE");
        assertThat(summary.getPackageName()).isEqualTo("Ultimate");
        assertThat(summary.getPurchaseId()).isEqualTo(371L);
        assertThat(summary.getStatus()).isEqualTo(PurchaseStatus.ACTIVE);
        assertThat(summary.isUsable()).isTrue();
        assertThat(summary.getProducts()).extracting("productCode").containsExactly("QR_MENU");
    }
}
