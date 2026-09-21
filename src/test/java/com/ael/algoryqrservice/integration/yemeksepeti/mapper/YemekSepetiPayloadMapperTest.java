package com.ael.algoryqrservice.integration.yemeksepeti.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class YemekSepetiPayloadMapperTest {

    private final YemekSepetiPayloadMapper mapper = new YemekSepetiPayloadMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsPartnerWebhookOrder() throws Exception {
        var node = objectMapper.readTree("""
                {
                  "order_id": "9d4a63b5-3e07-4440-96af-aa04797da3a0",
                  "order_code": "wxfr-2440-rtbs",
                  "status": "RECEIVED",
                  "comment": "Az pişmiş",
                  "order_type": "DELIVERY",
                  "client": {
                    "chain_id": "chain-1",
                    "id": "vendor-9",
                    "store_id": "nbet",
                    "name": "Test Restoran"
                  },
                  "customer": {
                    "first_name": "Ece",
                    "last_name": "Yılmaz",
                    "phone_number": "555"
                  },
                  "items": [
                    {
                      "sku": "HSMVTE",
                      "name": "Burger",
                      "pricing": { "quantity": 2, "unit_price": 47.75 }
                    }
                  ],
                  "payment": { "order_total": 95.50, "type": "PAID" },
                  "sys": { "created_at": "2026-09-21T06:00:00.000Z" }
                }
                """);

        assertThat(mapper.externalOrderId(node)).isEqualTo("9d4a63b5-3e07-4440-96af-aa04797da3a0");
        assertThat(mapper.orderNumber(node)).isEqualTo("wxfr-2440-rtbs");
        assertThat(mapper.packageStatus(node)).isEqualTo("RECEIVED");
        assertThat(mapper.vendorId(node)).isEqualTo("vendor-9");
        assertThat(mapper.totalAmount(node)).isEqualByComparingTo(new BigDecimal("95.50"));
        assertThat(mapper.customerName(node)).isEqualTo("Ece Yılmaz");
        assertThat(mapper.toOrderItems(node)).hasSize(1);
        assertThat(mapper.toOrderItems(node).getFirst().getProductName()).isEqualTo("Burger");
        assertThat(mapper.toOrderItems(node).getFirst().getQuantity()).isEqualTo(2);
    }
}
