package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.integration.ubereats.model.UberEatsOrder;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.OrderSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KitchenUberEatsMapperTest {

    @Test
    void detectsUberEatsSource() {
        assertThat(KitchenUberEatsMapper.isUberEatsSource("UBER_EATS")).isTrue();
        assertThat(KitchenUberEatsMapper.isUberEatsSource("uber-eats")).isTrue();
        assertThat(KitchenUberEatsMapper.isUberEatsSource("QR")).isFalse();
        assertThat(KitchenUberEatsMapper.isUberEatsSource(null)).isFalse();
    }

    @Test
    void mapsPackageStatusToKitchenColumns() {
        assertThat(KitchenUberEatsMapper.toKitchenStatus("Accepted")).isEqualTo(MenuOrderStatus.CONFIRMED);
        assertThat(KitchenUberEatsMapper.toKitchenStatus("picking")).isEqualTo(MenuOrderStatus.PREPARING);
        assertThat(KitchenUberEatsMapper.toKitchenStatus("Prepared")).isEqualTo(MenuOrderStatus.READY);
        assertThat(KitchenUberEatsMapper.toKitchenStatus("ready")).isEqualTo(MenuOrderStatus.READY);
        assertThat(KitchenUberEatsMapper.toKitchenStatus("Cancelled")).isEqualTo(MenuOrderStatus.CANCELLED);
        assertThat(KitchenUberEatsMapper.toKitchenStatus("READY_FOR_PICKUP")).isEqualTo(MenuOrderStatus.READY);
        assertThat(KitchenUberEatsMapper.toKitchenStatus("RECEIVED")).isEqualTo(MenuOrderStatus.CONFIRMED);
        assertThat(KitchenUberEatsMapper.isYemekSepetiSource("YEMEKSEPETI")).isTrue();
        assertThat(KitchenUberEatsMapper.isYemekSepetiSource("yemek-sepeti")).isTrue();
    }

    @Test
    void keepsAcceptedOrdersOnTheBoardAndDropsOldPreparedOnes() {
        LocalDate today = LocalDate.of(2026, 9, 19);
        UberEatsOrder accepted = UberEatsOrder.builder()
                .packageStatus("Accepted")
                .packageCreatedAt(LocalDateTime.of(2026, 9, 18, 22, 0))
                .build();
        UberEatsOrder preparedToday = UberEatsOrder.builder()
                .packageStatus("Prepared")
                .updatedAt(LocalDateTime.of(2026, 9, 19, 10, 0))
                .build();
        UberEatsOrder preparedYesterday = UberEatsOrder.builder()
                .packageStatus("Prepared")
                .updatedAt(LocalDateTime.of(2026, 9, 18, 10, 0))
                .packageCreatedAt(LocalDateTime.of(2026, 9, 18, 9, 0))
                .build();

        assertThat(KitchenUberEatsMapper.isActiveKitchenOrder(accepted, today)).isTrue();
        assertThat(KitchenUberEatsMapper.isActiveKitchenOrder(preparedToday, today)).isTrue();
        assertThat(KitchenUberEatsMapper.isActiveKitchenOrder(preparedYesterday, today)).isFalse();
    }

    @Test
    void mapsUberOrderOntoKitchenTicket() {
        UberEatsOrder order = UberEatsOrder.builder()
                .id(44L)
                .packageStatus("Accepted")
                .customerName("Ayşe")
                .note("Kapıya bırak")
                .totalAmount(new BigDecimal("120.00"))
                .currency("TRY")
                .packageCreatedAt(LocalDateTime.of(2026, 9, 19, 21, 0))
                .createdAt(LocalDateTime.of(2026, 9, 19, 21, 0))
                .updatedAt(LocalDateTime.of(2026, 9, 19, 21, 0))
                .build();
        List<UberEatsDtos.OrderItemResponse> items = List.of(
                UberEatsDtos.OrderItemResponse.builder()
                        .productName("Burger")
                        .quantity(2)
                        .unitPrice(new BigDecimal("50.00"))
                        .detail("Acısız")
                        .build()
        );

        var ticket = KitchenUberEatsMapper.toKitchenOrder(order, items);

        assertThat(ticket.getOrderSource()).isEqualTo(OrderSource.UBER_EATS);
        assertThat(ticket.getTableName()).isEqualTo("Uber Eats");
        assertThat(ticket.getStatus()).isEqualTo(MenuOrderStatus.CONFIRMED);
        assertThat(ticket.getCustomerName()).isEqualTo("Ayşe");
        assertThat(ticket.getItems()).hasSize(1);
        assertThat(ticket.getItems().getFirst().getProductName()).isEqualTo("Burger");
        assertThat(ticket.getItems().getFirst().getNote()).isEqualTo("Acısız");
        assertThat(ticket.getItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(ticket.getTotalAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void mapsYemekSepetiTicketWithAmount() {
        var ticket = KitchenUberEatsMapper.toTicket(
                12L,
                "RECEIVED",
                "Ece",
                null,
                new BigDecimal("95.50"),
                "TRY",
                LocalDateTime.of(2026, 9, 21, 9, 0),
                LocalDateTime.of(2026, 9, 21, 9, 0),
                LocalDateTime.of(2026, 9, 21, 9, 0),
                List.of(),
                OrderSource.YEMEKSEPETI,
                "Yemeksepeti"
        );

        assertThat(ticket.getOrderSource()).isEqualTo(OrderSource.YEMEKSEPETI);
        assertThat(ticket.getTableName()).isEqualTo("Yemeksepeti");
        assertThat(ticket.getStatus()).isEqualTo(MenuOrderStatus.CONFIRMED);
        assertThat(ticket.getTotalAmount()).isEqualByComparingTo("95.50");
        assertThat(ticket.getCustomerName()).isEqualTo("Ece");
    }
}
