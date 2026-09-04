package com.ael.algoryqrservice.integration.ubereats.mapper;

import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class UberEatsPayloadMapper {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    public List<UberEatsDtos.RestaurantResponse> toRestaurants(JsonNode root) {
        List<UberEatsDtos.RestaurantResponse> restaurants = new ArrayList<>();
        for (JsonNode node : listNodes(root, "restaurants", "stores", "content", "data", "items")) {
            String id = firstText(node, "id", "restaurantId", "storeId");
            if (id == null) {
                continue;
            }
            restaurants.add(UberEatsDtos.RestaurantResponse.builder()
                    .id(id)
                    .name(firstText(node, "name", "restaurantName", "storeName"))
                    .address(firstText(node, "address", "addressText", "fullAddress"))
                    .build());
        }
        return restaurants;
    }

    public List<UberEatsDtos.ProductResponse> toProducts(JsonNode root) {
        List<UberEatsDtos.ProductResponse> products = new ArrayList<>();
        JsonNode categories = firstNode(root, "categories", "sections", "menuCategories");
        if (categories != null && categories.isArray()) {
            for (JsonNode category : categories) {
                String categoryName = firstText(category, "name", "categoryName", "title");
                for (JsonNode product : listNodes(category, "products", "items", "meals")) {
                    addProduct(products, product, categoryName);
                }
            }
            if (!products.isEmpty()) {
                return products;
            }
        }
        for (JsonNode product : listNodes(root, "products", "items", "content", "data")) {
            addProduct(products, product, firstText(product, "categoryName", "category", "sectionName"));
        }
        return products;
    }

    public List<JsonNode> toOrderNodes(JsonNode root) {
        List<JsonNode> orders = new ArrayList<>();
        if (root == null || root.isNull()) {
            return orders;
        }
        if (isOrderLike(root)) {
            orders.add(root);
            return orders;
        }
        for (JsonNode node : listNodes(root, "orders", "packages", "content", "data", "items")) {
            if (isOrderLike(node) || node.isObject()) {
                orders.add(node);
            }
        }
        return orders;
    }

    public String externalOrderId(JsonNode node) {
        return firstText(node, "id", "orderId", "packageId", "externalOrderId");
    }

    public String orderNumber(JsonNode node) {
        return firstText(node, "orderCode", "orderNumber", "order.code", "shipmentNumber");
    }

    public String deliveryType(JsonNode node) {
        String text = firstText(
                node,
                "deliveryTypeText",
                "deliveryProviderName",
                "courierType",
                "deliveryType",
                "deliveryAddressType",
                "delivery.type"
        );
        return text;
    }

    public String paymentMethod(JsonNode node) {
        String text = firstText(
                node,
                "paymentMethodText",
                "payment.paymentMethodText",
                "paymentTypeText",
                "payment.paymentTypeText",
                "paymentMethodName",
                "payment.paymentMethodName",
                "paymentType",
                "payment.type",
                "paymentMethod"
        );
        if (text != null) {
            return text;
        }
        Boolean cashOnDelivery = firstBoolean(node, "isCod", "payment.isCod", "cashOnDelivery");
        if (Boolean.TRUE.equals(cashOnDelivery)) {
            return "Kapıda ödeme";
        }
        return null;
    }

    public String restaurantId(JsonNode node) {
        return firstText(node, "restaurantId", "storeId", "restaurant.id", "store.id");
    }

    public String sellerId(JsonNode node) {
        return firstText(node, "sellerId", "supplierId", "store.sellerId");
    }

    public String packageStatus(JsonNode node) {
        return firstText(node, "packageStatus", "status", "orderStatus");
    }

    public BigDecimal totalAmount(JsonNode node) {
        return firstDecimal(node, "totalPrice", "totalAmount", "amount", "price");
    }

    public String currency(JsonNode node) {
        String value = firstText(node, "currency", "currencyCode");
        return value == null ? "TRY" : value;
    }

    public String customerName(JsonNode node) {
        String combined = firstText(node, "customerName", "customer.fullName", "customer.name");
        if (combined != null) {
            return combined;
        }
        String first = firstText(node, "customer.firstName", "customer.first_name");
        String last = firstText(node, "customer.lastName", "customer.last_name");
        if (first == null && last == null) {
            return null;
        }
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    public String customerPhone(JsonNode node) {
        return firstText(
                node,
                "customerPhone",
                "phoneNumber",
                "customer.phone",
                "customer.gsm",
                "phone",
                "address.phone"
        );
    }

    public String deliveryAddress(JsonNode node) {
        String text = firstText(
                node,
                "deliveryAddress",
                "deliveryAddress.address1",
                "deliveryAddress.fullAddress",
                "shippingAddress.address1"
        );
        if (text != null) {
            return text;
        }
        JsonNode address = firstNode(node, "address", "deliveryAddress", "shippingAddress");
        if (address == null || !address.isObject()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, firstText(address, "neighborhood"));
        addIfPresent(parts, firstText(address, "address1", "address", "fullAddress", "street"));
        addIfPresent(parts, apartmentPart(address));
        addIfPresent(parts, firstText(address, "district", "city", "province"));
        addIfPresent(parts, firstText(node, "addressDescription"));
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    public String note(JsonNode node) {
        return firstText(node, "customerNote", "note", "orderNote", "description");
    }

    public LocalDateTime packageCreatedAt(JsonNode node) {
        return firstDateTime(node, "packageCreationDate", "createdDate", "orderDate", "createdAt", "packageCreatedAt");
    }

    public List<UberEatsDtos.OrderItemResponse> toOrderItems(JsonNode node) {
        List<UberEatsDtos.OrderItemResponse> items = new ArrayList<>();
        for (JsonNode line : listNodes(node, "lines", "items", "products", "orderItems")) {
            String detail = lineDetail(line);
            items.add(UberEatsDtos.OrderItemResponse.builder()
                    .productId(firstText(line, "productId", "id", "itemId"))
                    .productName(firstText(line, "productName", "name", "title", "nameDisplay"))
                    .quantity(lineQuantity(line))
                    .unitPrice(firstDecimal(line, "unitSellingPrice", "unitPrice", "price", "salePrice"))
                    .options(detail)
                    .detail(detail)
                    .build());
        }
        return items;
    }

    private void addProduct(List<UberEatsDtos.ProductResponse> products, JsonNode product, String categoryName) {
        String id = firstText(product, "id", "productId", "itemId");
        if (id == null) {
            return;
        }
        products.add(UberEatsDtos.ProductResponse.builder()
                .id(id)
                .name(firstText(product, "name", "productName", "title"))
                .description(firstText(product, "description", "detail"))
                .categoryName(categoryName)
                .price(firstDecimal(product, "price", "salePrice", "sellingPrice"))
                .currency(firstText(product, "currency", "currencyCode") == null
                        ? "TRY"
                        : firstText(product, "currency", "currencyCode"))
                .imageUrl(firstText(product, "imageUrl", "image", "photoUrl", "images.0"))
                .available(isAvailable(product))
                .build());
    }

    private boolean isAvailable(JsonNode product) {
        Boolean selling = firstBoolean(product, "selling", "isSelling", "available", "isAvailable", "active");
        if (selling != null) {
            return selling;
        }
        String status = firstText(product, "status", "sellingStatus");
        if (status == null) {
            return true;
        }
        String normalized = status.toLowerCase(Locale.ROOT);
        return !(normalized.contains("passive")
                || normalized.contains("sold")
                || normalized.contains("unavail")
                || normalized.contains("closed"));
    }

    private int lineQuantity(JsonNode line) {
        JsonNode packageItems = firstNode(line, "items");
        if (packageItems != null && packageItems.isArray() && !packageItems.isEmpty()) {
            return packageItems.size();
        }
        return firstInt(line, "quantity", "qty", "count");
    }

    private String lineDetail(JsonNode line) {
        List<String> parts = new ArrayList<>();
        collectLineOptions(line, parts, 0);
        addIfPresent(parts, firstText(line, "note", "description"));
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private void collectLineOptions(JsonNode line, List<String> parts, int depth) {
        if (line == null || depth > 3) {
            return;
        }
        appendNamedNodes(parts, firstNode(line, "extraIngredients"), null);
        appendNamedNodes(parts, firstNode(line, "removedIngredients"), "Çıkarılan");
        JsonNode modifiers = firstNode(line, "modifierProducts", "modifiers", "options");
        if (modifiers != null && modifiers.isArray()) {
            for (JsonNode modifier : modifiers) {
                String name = modifier.isTextual() ? modifier.asText() : firstText(modifier, "name", "title");
                addIfPresent(parts, name);
                collectLineOptions(modifier, parts, depth + 1);
            }
            return;
        }
        if (modifiers != null && modifiers.isTextual()) {
            addIfPresent(parts, modifiers.asText());
        }
    }

    private void appendNamedNodes(List<String> parts, JsonNode nodes, String suffix) {
        if (nodes == null || nodes.isNull()) {
            return;
        }
        if (nodes.isTextual()) {
            addIfPresent(parts, suffix == null ? nodes.asText() : nodes.asText() + " (" + suffix + ")");
            return;
        }
        if (!nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            String name = node.isTextual() ? node.asText() : firstText(node, "name", "title", "optionNameDisplay");
            if (name == null) {
                continue;
            }
            addIfPresent(parts, suffix == null ? name : name + " (" + suffix + ")");
        }
    }

    private String apartmentPart(JsonNode address) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, labeledPart("Apt", firstText(address, "apartmentNumber")));
        addIfPresent(parts, labeledPart("Kat", firstText(address, "floor")));
        addIfPresent(parts, labeledPart("Kapı", firstText(address, "doorNumber")));
        addIfPresent(parts, labeledPart("Firma", firstText(address, "company")));
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private String labeledPart(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return label + ": " + value.trim();
    }

    private boolean isOrderLike(JsonNode node) {
        return node != null && node.isObject() && externalOrderId(node) != null;
    }

    private List<JsonNode> listNodes(JsonNode root, String... keys) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        if (root.isArray()) {
            List<JsonNode> nodes = new ArrayList<>();
            root.forEach(nodes::add);
            return nodes;
        }
        for (String key : keys) {
            JsonNode node = firstNode(root, key);
            if (node != null && node.isArray()) {
                List<JsonNode> nodes = new ArrayList<>();
                node.forEach(nodes::add);
                return nodes;
            }
        }
        return List.of();
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
                if (part.chars().allMatch(Character::isDigit) && current.isArray()) {
                    int index = Integer.parseInt(part);
                    current = index < current.size() ? current.get(index) : null;
                } else {
                    current = current.get(part);
                }
            }
            if (current != null && !current.isNull()) {
                return current;
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.isValueNode() ? node.asText() : null;
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal firstDecimal(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int firstInt(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(node.asText()));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private Boolean firstBoolean(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value) || "1".equals(value);
    }

    private LocalDateTime firstDateTime(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long epoch = node.asLong();
            if (epoch > 1_000_000_000_000L) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZONE);
            }
            if (epoch > 1_000_000_000L) {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZONE);
            }
        }
        String text = node.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZONE);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text);
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
}
