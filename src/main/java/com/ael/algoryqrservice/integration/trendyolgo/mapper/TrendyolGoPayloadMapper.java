package com.ael.algoryqrservice.integration.trendyolgo.mapper;

import com.ael.algoryqrservice.integration.trendyolgo.model.dto.TrendyolGoDtos;
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
public class TrendyolGoPayloadMapper {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    public List<TrendyolGoDtos.RestaurantResponse> toRestaurants(JsonNode root) {
        List<TrendyolGoDtos.RestaurantResponse> restaurants = new ArrayList<>();
        for (JsonNode node : listNodes(root, "restaurants", "stores", "content", "data", "items")) {
            String id = firstText(node, "id", "restaurantId", "storeId");
            if (id == null) {
                continue;
            }
            restaurants.add(TrendyolGoDtos.RestaurantResponse.builder()
                    .id(id)
                    .name(firstText(node, "name", "restaurantName", "storeName"))
                    .address(firstText(node, "address", "addressText", "fullAddress"))
                    .build());
        }
        return restaurants;
    }

    public List<TrendyolGoDtos.ProductResponse> toProducts(JsonNode root) {
        List<TrendyolGoDtos.ProductResponse> products = new ArrayList<>();
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
        return firstText(node, "customerPhone", "customer.phone", "customer.gsm", "phone");
    }

    public String deliveryAddress(JsonNode node) {
        String text = firstText(
                node,
                "deliveryAddress",
                "address",
                "deliveryAddress.address1",
                "deliveryAddress.fullAddress",
                "shippingAddress.address1"
        );
        if (text != null) {
            return text;
        }
        JsonNode address = firstNode(node, "deliveryAddress", "shippingAddress", "address");
        if (address == null || !address.isObject()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, firstText(address, "address1", "address", "fullAddress", "street"));
        addIfPresent(parts, firstText(address, "district", "neighborhood"));
        addIfPresent(parts, firstText(address, "city", "province"));
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    public String note(JsonNode node) {
        return firstText(node, "note", "orderNote", "description", "customerNote");
    }

    public LocalDateTime packageCreatedAt(JsonNode node) {
        return firstDateTime(node, "packageCreationDate", "createdDate", "orderDate", "createdAt", "packageCreatedAt");
    }

    public List<TrendyolGoDtos.OrderItemResponse> toOrderItems(JsonNode node) {
        List<TrendyolGoDtos.OrderItemResponse> items = new ArrayList<>();
        for (JsonNode line : listNodes(node, "lines", "items", "products", "orderItems")) {
            items.add(TrendyolGoDtos.OrderItemResponse.builder()
                    .productId(firstText(line, "productId", "id", "itemId"))
                    .productName(firstText(line, "productName", "name", "title"))
                    .quantity(firstInt(line, "quantity", "qty", "count"))
                    .unitPrice(firstDecimal(line, "price", "unitPrice", "salePrice"))
                    .options(optionsText(line))
                    .build());
        }
        return items;
    }

    private void addProduct(List<TrendyolGoDtos.ProductResponse> products, JsonNode product, String categoryName) {
        String id = firstText(product, "id", "productId", "itemId");
        if (id == null) {
            return;
        }
        products.add(TrendyolGoDtos.ProductResponse.builder()
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

    private String optionsText(JsonNode line) {
        JsonNode extras = firstNode(line, "extraIngredients", "ingredients", "options", "modifiers");
        if (extras == null || extras.isNull()) {
            return firstText(line, "note", "description");
        }
        if (extras.isTextual()) {
            return extras.asText();
        }
        if (extras.isArray()) {
            List<String> names = new ArrayList<>();
            for (JsonNode extra : extras) {
                String name = extra.isTextual() ? extra.asText() : firstText(extra, "name", "title");
                addIfPresent(names, name);
            }
            return names.isEmpty() ? null : String.join(", ", names);
        }
        return extras.toString();
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
