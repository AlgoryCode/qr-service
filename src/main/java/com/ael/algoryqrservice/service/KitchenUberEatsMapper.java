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
        return matchesSource(source, "UBER_EATS", "UBEREATS", "UBER");
    }

    public static boolean isYemekSepetiSource(String source) {
        return matchesSource(source, "YEMEKSEPETI", "YEMEK_SEPETI", "YS");
    }

    public static String normalizeStatus(String packageStatus) {
        if (packageStatus == null) {
            return "";
        }
        return packageStatus.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    public static MenuOrderStatus toKitchenStatus(String packageStatus) {
        String status = normalizeStatus(packageStatus);
        if (status.contains("cancel") || "unsupplied".equals(status)) {
            return MenuOrderStatus.CANCELLED;
        }
        if (status.contains("reject")) {
            return MenuOrderStatus.REJECTED;
        }
        if ("picking".equals(status)) {
            return MenuOrderStatus.PREPARING;
        }
        if ("prepared".equals(status)
                || "ready".equals(status)
                || "readyforpickup".equals(status)
                || "dispatched".equals(status)
                || "delivered".equals(status)
                || "pickedup".equals(status)
                || "completed".equals(status)) {
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
                items.add(toTicketItem(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        firstText(item.getDetail(), item.getOptions())
                ));
            }
        }

        return toTicket(
                order.getId(),
                order.getPackageStatus(),
                order.getCustomerName(),
                order.getNote(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getPackageCreatedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items,
                OrderSource.UBER_EATS,
                "Uber Eats"
        );
    }

    public static MenuOrderDtos.OrderResponse toTicket(
            Long id,
            String packageStatus,
            String customerName,
            String note,
            BigDecimal totalAmount,
            String currency,
            LocalDateTime packageCreatedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<MenuOrderDtos.OrderItemResponse> items,
            OrderSource source,
            String tableName
    ) {
        LocalDateTime when = packageCreatedAt != null ? packageCreatedAt : createdAt;
        MenuOrderStatus status = toKitchenStatus(packageStatus);
        return MenuOrderDtos.OrderResponse.builder()
                .id(id)
                .tableName(tableName)
                .customerName(customerName)
                .status(status)
                .orderSource(source)
                .totalAmount(totalAmount)
                .currency(currency)
                .note(note)
                .items(items == null ? List.of() : items)
                .submittedAt(when)
                .confirmedAt(when)
                .cancelledAt(status == MenuOrderStatus.CANCELLED || status == MenuOrderStatus.REJECTED ? updatedAt : null)
                .rejectedAt(status == MenuOrderStatus.REJECTED ? updatedAt : null)
                .preparedAt(status == MenuOrderStatus.PREPARING || status == MenuOrderStatus.READY ? updatedAt : null)
                .readyAt(status == MenuOrderStatus.READY ? updatedAt : null)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static MenuOrderDtos.OrderItemResponse toTicketItem(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            String note
    ) {
        return MenuOrderDtos.OrderItemResponse.builder()
                .productId(parseLong(productId))
                .productName(productName)
                .unitPrice(unitPrice)
                .quantity(Math.max(quantity, 1))
                .note(note)
                .lineTotal(lineTotal(unitPrice, quantity))
                .selectedOptions(List.of())
                .build();
    }

    public static boolean isActiveMarketplaceKitchenOrder(
            String packageStatus,
            LocalDateTime updatedAt,
            LocalDateTime packageCreatedAt,
            LocalDate today,
            boolean includeReceived
    ) {
        String status = normalizeStatus(packageStatus);
        if (includeReceived && "received".equals(status)) {
            return true;
        }
        if ("accepted".equals(status) || "picking".equals(status)) {
            return true;
        }
        if ("prepared".equals(status)
                || "ready".equals(status)
                || "readyforpickup".equals(status)
                || "dispatched".equals(status)) {
            return isOnDay(updatedAt, today) || isOnDay(packageCreatedAt, today);
        }
        return false;
    }

    private static boolean matchesSource(String source, String... aliases) {
        if (source == null || source.isBlank()) {
            return false;
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (String alias : aliases) {
            if (alias.equals(normalized)) {
                return true;
            }
        }
        return false;
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
