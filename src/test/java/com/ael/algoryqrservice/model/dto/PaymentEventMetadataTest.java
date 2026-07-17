package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventMetadataTest {

    @Test
    void from_whenInstallmentFieldsMissing_thenDefaultToOneTimeShape() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("purchaseId", 19L);
        metadata.put("userId", 1L);
        metadata.put("packageId", 2L);
        metadata.put("packageCode", "PRO_PACKAGE");
        metadata.put("purchaseConversationId", "qr-purchase-19-e6d5b588");

        PaymentCompletedEventDto event = new PaymentCompletedEventDto();
        event.setPaymentId("36672108");
        event.setConversationId("qr-purchase-19-e6d5b588");
        event.setSourceMetadata(metadata);
        event.setPeriodStart("2026-07-16");
        event.setPeriodEnd("2026-08-14");

        PaymentEventMetadata parsed = PaymentEventMetadata.from(event);

        assertThat(parsed.purchaseId()).isEqualTo(19L);
        assertThat(parsed.packageCode()).isEqualTo(CatalogPackages.PRO_PACKAGE);
        assertThat(parsed.installmentNumber()).isEqualTo(1);
        assertThat(parsed.installmentCount()).isEqualTo(1);
        assertThat(parsed.installmentId()).isEqualTo("36672108");
    }
}
