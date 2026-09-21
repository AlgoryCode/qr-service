package com.ael.algoryqrservice.integration.yemeksepeti.mapper;

import com.ael.algoryqrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class YemekSepetiPayloadMapper {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    public List<JsonNode> toOrderNodes(JsonNode root) {
        List<JsonNode> orders = new ArrayList<>();
        if (root == null || root.isNull()) {
            return orders;
        }
        if (isOrderLike(root)) {
            orders.add(root);
            return orders;
        }
        JsonNode listed = firstNode(root, "orders", "content", "data", "items");
        if (listed != null && listed.isArray()) {
            listed.forEach(node -> {
                if (node != null && node.isObject()) {
                    orders.add(node);
                }
            });
        }
        return orders;
    }

    public String externalOrderId(JsonNode node) {
        return firstText(node, "order_id", "orderId", "id");
    }

    public String orderNumber(JsonNode node) {
        return firstText(node, "order_code", "orderCode", "external_order_id", "externalOrderId");
    }

    public String deliveryType(JsonNode node) {
        return firstText(node, "order_type", "orderType", "deliveryType");
    }

    public String paymentMethod(JsonNode node) {
        return firstText(node, "payment.type", "paymentMethod", "payment.paymentMethod");
    }

    public String vendorId(JsonNode node) {
        return firstText(
                node,
                "client.id",
                "client.store_id",
                "client.external_partner_config_id",
                "vendorId",
                "store_id",
                "storeId"
        );
    }

    public String chainId(JsonNode node) {
        return firstText(node, "client.chain_id", "chain_id", "chainId");
    }

    public String vendorName(JsonNode node) {
        return firstText(node, "client.name", "vendorName", "storeName");
    }

    public String packageStatus(JsonNode node) {
        return firstText(node, "status", "packageStatus", "orderStatus");
    }

    public BigDecimal totalAmount(JsonNode node) {
        return firstDecimal(node, "payment.order_total", "payment.sub_total", "totalAmount", "order_total");
    }

    public String currency(JsonNode node) {
        String value = firstText(node, "payment.currency", "currency");
        return value == null ? "TRY" : value;
    }

    public String customerName(JsonNode node) {
        String combined = firstText(node, "customer.name", "customer.fullName");
        if (combined != null) {
            return combined;
        }
        String first = firstText(node, "customer.first_name", "customer.firstName");
        String last = firstText(node, "customer.last_name", "customer.lastName");
        if (first == null && last == null) {
            return null;
        }
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    public String customerPhone(JsonNode node) {
        return firstText(node, "customer.phone_number", "customer.phone", "customerPhone");
    }

    public String deliveryAddress(JsonNode node) {
        String formatted = firstText(
                node,
                "customer.delivery_address.formattedAddress",
                "customer.delivery_address.street",
                "deliveryAddress"
        );
        if (formatted != null) {
            return formatted;
        }
        JsonNode address = firstNode(node, "customer.delivery_address", "delivery_address");
        if (address == null || !address.isObject()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, firstText(address, "street"));
        addIfPresent(parts, firstText(address, "number"));
        addIfPresent(parts, firstText(address, "city"));
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    public String note(JsonNode node) {
        return firstText(node, "comment", "note", "customerNote");
    }

    public LocalDateTime packageCreatedAt(JsonNode node) {
        return firstDateTime(node, "sys.created_at", "promised_for", "accepted_for", "createdAt", "packageCreatedAt");
    }

    public List<YemekSepetiDtos.OrderItemResponse> toOrderItems(JsonNode node) {
        List<YemekSepetiDtos.OrderItemResponse> items = new ArrayList<>();
        JsonNode listed = firstNode(node, "items", "products");
        if (listed == null || !listed.isArray()) {
            return items;
        }
        for (JsonNode line : listed) {
            if (line == null || !line.isObject()) {
                continue;
            }
            int quantity = firstInt(line, "pricing.quantity", "original_pricing.quantity", "quantity");
            items.add(YemekSepetiDtos.OrderItemResponse.builder()
                    .productId(firstText(line, "sku", "_id", "id"))
                    .productName(firstText(line, "name", "productName"))
                    .quantity(Math.max(quantity, 1))
                    .unitPrice(firstDecimal(line, "pricing.unit_price", "original_pricing.unit_price", "unitPrice"))
                    .options(firstText(line, "instructions"))
                    .detail(firstText(line, "instructions", "comment"))
                    .build());
        }
        return items;
    }

    private boolean isOrderLike(JsonNode node) {
        return node != null && node.isObject() && (hasText(externalOrderId(node)) || hasText(firstText(node, "status")));
    }

    private JsonNode firstNode(JsonNode root, String... paths) {
        if (root == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode current = root;
            for (String part : path.split("\\.")) {
                if (current == null) {
                    break;
                }
                current = current.get(part);
            }
            if (current != null && !current.isNull()) {
                return current;
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal firstDecimal(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asText().trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int firstInt(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull()) {
            return 1;
        }
        if (node.isNumber()) {
            return Math.max(node.asInt(1), 1);
        }
        try {
            return Math.max(Integer.parseInt(node.asText().trim()), 1);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private LocalDateTime firstDateTime(JsonNode root, String... paths) {
        String raw = firstText(root, paths);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(raw), ZONE);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(raw);
            } catch (DateTimeParseException exception) {
                return null;
            }
        }
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
