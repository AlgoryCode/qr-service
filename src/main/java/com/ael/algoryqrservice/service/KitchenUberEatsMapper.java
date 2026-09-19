package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.integration.ubereats.model.UberEatsOrder;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.OrderSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KitchenUberEatsMapper {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    static final List<String> KITCHEN_STATUSES = List.of("accepted", "picking", "prepared", "ready");

    private KitchenUberEatsMapper() {
    }

    public static boolean isUberEatsSource(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "UBER_EATS".equals(normalized) || "UBEREATS".equals(normalized) || "UBER".equals(normalized);
    }

    public static String normalizeStatus(String packageStatus) {
        if (packageStatus == null) {
            return "";
        }
        return packageStatus.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    public static MenuOrderStatus toKitchenStatus(String packageStatus) {
        String status = normalizeStatus(packageStatus);
        if ("picking".equals(status)) {
            return MenuOrderStatus.PREPARING;
        }
        if ("prepared".equals(status) || "ready".equals(status)) {
            return MenuOrderStatus.READY;
        }
        return MenuOrderStatus.CONFIRMED;
    }

    public static boolean isActiveKitchenOrder(UberEatsOrder order, LocalDate today) {
        if (order == null) {
            return false;
        }
        String status = normalizeStatus(order.getPackageStatus());
        if ("accepted".equals(status) || "picking".equals(status)) {
            return true;
        }
        if ("prepared".equals(status) || "ready".equals(status)) {
            return isOnDay(order.getUpdatedAt(), today) || isOnDay(order.getPackageCreatedAt(), today);
        }
        return false;
    }

    public static MenuOrderDtos.OrderResponse toKitchenOrder(
            UberEatsOrder order,
            List<UberEatsDtos.OrderItemResponse> uberItems
    ) {
        List<MenuOrderDtos.OrderItemResponse> items = new ArrayList<>();
        if (uberItems != null) {
            for (UberEatsDtos.OrderItemResponse item : uberItems) {
                if (item == null) {
                    continue;
                }
                String optionNote = firstText(item.getDetail(), item.getOptions());
                items.add(MenuOrderDtos.OrderItemResponse.builder()
                        .productId(parseLong(item.getProductId()))
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(Math.max(item.getQuantity(), 1))
                        .note(optionNote)
                        .lineTotal(lineTotal(item.getUnitPrice(), item.getQuantity()))
                        .selectedOptions(List.of())
                        .build());
            }
        }

        LocalDateTime when = order.getPackageCreatedAt() != null ? order.getPackageCreatedAt() : order.getCreatedAt();
        MenuOrderStatus status = toKitchenStatus(order.getPackageStatus());
        return MenuOrderDtos.OrderResponse.builder()
                .id(order.getId())
                .tableName("Uber Eats")
                .customerName(order.getCustomerName())
                .status(status)
                .orderSource(OrderSource.UBER_EATS)
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .note(order.getNote())
                .items(items)
                .submittedAt(when)
                .confirmedAt(when)
                .preparedAt(status == MenuOrderStatus.PREPARING || status == MenuOrderStatus.READY ? order.getUpdatedAt() : null)
                .readyAt(status == MenuOrderStatus.READY ? order.getUpdatedAt() : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private static boolean isOnDay(LocalDateTime value, LocalDate today) {
        return value != null && today != null && value.toLocalDate().equals(today);
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null) {
            return null;
        }
        return unitPrice.multiply(BigDecimal.valueOf(Math.max(quantity, 1)));
    }
}
