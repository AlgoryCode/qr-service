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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public Map<String, Object> toUpsertBody(UberEatsDtos.CreateProductRequest request) {
        if (request == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> body = coreUpsertBody(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getCurrency(),
                request.getCategoryName(),
                request.getImageUrl(),
                request.getAvailable(),
                null
        );
        appendModifierGroups(body, request.getModifierGroups());
        return body;
    }

    public Map<String, Object> toUpsertBody(JsonNode productData) {
        if (productData == null || productData.isNull()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> body = coreUpsertBody(
                firstText(productData, "name"),
                firstText(productData, "description"),
                firstDecimal(productData, "price"),
                firstText(productData, "currency"),
                firstText(productData, "categoryName", "category", "subcategory"),
                firstText(productData, "imageUrl"),
                firstBoolean(productData, "available"),
                firstText(productData, "externalProductId", "partnerProductId", "id")
        );
        appendModifierGroupsFromNode(body, productData);
        return body;
    }

    public UberEatsDtos.ProductResponse toCreatedProduct(
            JsonNode response,
            UberEatsDtos.CreateProductRequest request
    ) {
        List<UberEatsDtos.ProductResponse> products = toProducts(response);
        if (!products.isEmpty()) {
            return products.getFirst();
        }
        String id = firstText(response, "id", "productId");
        return UberEatsDtos.ProductResponse.builder()
                .id(id == null ? "" : id)
                .name(request.getName())
                .description(request.getDescription())
                .categoryName(request.getCategoryName())
                .price(request.getPrice())
                .currency(request.getCurrency() == null ? "TRY" : request.getCurrency())
                .imageUrl(request.getImageUrl())
                .available(request.getAvailable() == null || request.getAvailable())
                .build();
    }

    public List<UberEatsDtos.ProductResponse> toProducts(JsonNode root) {
        Map<String, JsonNode> catalog = indexCatalogProducts(root);
        List<UberEatsDtos.ProductResponse> products = new ArrayList<>();
        JsonNode categories = firstNode(root, "categories", "sections", "menuCategories");
        if (categories != null && categories.isArray()) {
            for (JsonNode category : categories) {
                String categoryName = firstText(category, "name", "categoryName", "title");
                for (JsonNode product : listNodes(category, "products", "items", "meals")) {
                    addProduct(products, enrichFromCatalog(product, catalog), categoryName);
                }
            }
        }
        if (products.isEmpty()) {
            for (JsonNode product : catalog.values()) {
                addProduct(
                        products,
                        product,
                        firstText(product, "categoryName", "category", "sectionName")
                );
            }
            return products;
        }
        for (Map.Entry<String, JsonNode> entry : catalog.entrySet()) {
            boolean alreadyListed = false;
            for (UberEatsDtos.ProductResponse existing : products) {
                if (entry.getKey().equals(existing.getId())) {
                    alreadyListed = true;
                    break;
                }
            }
            if (!alreadyListed) {
                addProduct(
                        products,
                        entry.getValue(),
                        firstText(entry.getValue(), "categoryName", "category", "sectionName")
                );
            }
        }
        return products;
    }

    private Map<String, JsonNode> indexCatalogProducts(JsonNode root) {
        Map<String, JsonNode> catalog = new LinkedHashMap<>();
        for (JsonNode product : listNodes(root, "products", "items", "content", "data")) {
            String id = productId(product);
            if (id != null) {
                catalog.put(id, product);
            }
        }
        return catalog;
    }

    private JsonNode enrichFromCatalog(JsonNode product, Map<String, JsonNode> catalog) {
        String id = productId(product);
        if (id == null) {
            return product;
        }
        JsonNode full = catalog.get(id);
        if (full == null) {
            return product;
        }
        if (productName(product) != null) {
            return product;
        }
        return full;
    }

    private String productId(JsonNode product) {
        return firstText(
                product,
                "id",
                "productId",
                "itemId",
                "stockId",
                "stock.id",
                "product.id",
                "productContentId"
        );
    }

    private String productName(JsonNode product) {
        return firstText(
                product,
                "name",
                "productName",
                "title",
                "nameDisplay",
                "stockName",
                "stockNameDisplay",
                "productContentName",
                "contentName",
                "product.name",
                "product.title",
                "product.productName",
                "stock.name"
        );
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

    private Map<String, Object> coreUpsertBody(
            String name,
            String description,
            BigDecimal price,
            String currency,
            String categoryName,
            String imageUrl,
            Boolean available,
            String externalId
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (hasText(externalId)) {
            body.put("id", externalId.trim());
        }
        if (hasText(name)) {
            body.put("name", name.trim());
        }
        if (hasText(description)) {
            body.put("description", description.trim());
        }
        if (price != null) {
            body.put("price", price);
            body.put("salePrice", price);
        }
        body.put("currency", hasText(currency) ? currency.trim() : "TRY");
        if (hasText(categoryName)) {
            body.put("categoryName", categoryName.trim());
        }
        if (hasText(imageUrl)) {
            body.put("imageUrl", imageUrl.trim());
        }
        body.put("selling", available == null || available);
        body.put("status", available != null && !available ? "PASSIVE" : "ACTIVE");
        return body;
    }

    private void appendModifierGroupsFromNode(Map<String, Object> body, JsonNode productData) {
        JsonNode groups = firstNode(productData, "modifierGroups");
        if (groups == null || !groups.isArray()) {
            return;
        }
        List<UberEatsDtos.ModifierGroupRequest> parsed = new ArrayList<>();
        for (JsonNode group : groups) {
            parsed.add(UberEatsDtos.ModifierGroupRequest.builder()
                    .name(firstText(group, "name"))
                    .required(Boolean.TRUE.equals(firstBoolean(group, "required")))
                    .minSelect(firstInteger(group, "minSelect"))
                    .maxSelect(firstInteger(group, "maxSelect"))
                    .options(parseModifierOptions(group))
                    .build());
        }
        appendModifierGroups(body, parsed);
    }

    private List<UberEatsDtos.ModifierOptionRequest> parseModifierOptions(JsonNode group) {
        List<UberEatsDtos.ModifierOptionRequest> options = new ArrayList<>();
        for (JsonNode option : listNodes(group, "options", "modifierOptions")) {
            String name = firstText(option, "name", "title");
            if (name == null) {
                continue;
            }
            options.add(UberEatsDtos.ModifierOptionRequest.builder()
                    .name(name)
                    .price(firstDecimal(option, "price", "priceDelta"))
                    .build());
        }
        return options;
    }

    private void appendModifierGroups(Map<String, Object> body, List<UberEatsDtos.ModifierGroupRequest> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        List<Map<String, Object>> modifierProducts = new ArrayList<>();
        List<Map<String, Object>> extraIngredients = new ArrayList<>();
        for (UberEatsDtos.ModifierGroupRequest group : groups) {
            Map<String, Object> mapped = mapModifierGroup(group);
            if (mapped == null) {
                continue;
            }
            modifierProducts.add(mapped);
            if (!group.isRequired()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> options = (List<Map<String, Object>>) mapped.get("modifierOptions");
                extraIngredients.addAll(copyOptionMaps(options));
            }
        }
        if (!modifierProducts.isEmpty()) {
            body.put("modifierProducts", modifierProducts);
        }
        if (!extraIngredients.isEmpty()) {
            body.put("extraIngredients", extraIngredients);
        }
    }

    private Map<String, Object> mapModifierGroup(UberEatsDtos.ModifierGroupRequest group) {
        if (group == null || !hasText(group.getName())) {
            return null;
        }
        List<Map<String, Object>> options = mapModifierOptions(group.getOptions());
        if (options.isEmpty()) {
            return null;
        }
        int minSelect = resolvedMin(group.isRequired(), group.getMinSelect());
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("name", group.getName().trim());
        mapped.put("required", group.isRequired());
        mapped.put("minSelect", minSelect);
        mapped.put("maxSelect", resolvedMax(minSelect, group.getMaxSelect()));
        mapped.put("modifierOptions", options);
        return mapped;
    }

    private List<Map<String, Object>> mapModifierOptions(List<UberEatsDtos.ModifierOptionRequest> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (UberEatsDtos.ModifierOptionRequest option : options) {
            if (option == null || !hasText(option.getName())) {
                continue;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", option.getName().trim());
            body.put("price", option.getPrice() == null ? BigDecimal.ZERO : option.getPrice());
            mapped.add(body);
        }
        return mapped;
    }

    private List<Map<String, Object>> copyOptionMaps(List<Map<String, Object>> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copies = new ArrayList<>();
        for (Map<String, Object> option : options) {
            copies.add(new LinkedHashMap<>(option));
        }
        return copies;
    }

    private int resolvedMin(boolean required, Integer minSelect) {
        int min = minSelect == null ? 0 : Math.max(0, minSelect);
        return required ? Math.max(1, min) : min;
    }

    private int resolvedMax(int minSelect, Integer maxSelect) {
        int max = maxSelect == null ? Math.max(1, minSelect) : maxSelect;
        return Math.max(minSelect, max);
    }

    private Integer firstInteger(JsonNode root, String... paths) {
        JsonNode node = firstNode(root, paths);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        try {
            return Integer.parseInt(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void addProduct(List<UberEatsDtos.ProductResponse> products, JsonNode product, String categoryName) {
        String id = productId(product);
        if (id == null) {
            return;
        }
        for (UberEatsDtos.ProductResponse existing : products) {
            if (id.equals(existing.getId())) {
                return;
            }
        }
        String name = productName(product);
        String description = firstText(
                product,
                "description",
                "detail",
                "productDescription",
                "stockDescription",
                "product.description"
        );
        BigDecimal price = firstDecimal(
                product,
                "price",
                "salePrice",
                "sellingPrice",
                "listPrice",
                "unitPrice",
                "amount",
                "product.price",
                "product.salePrice",
                "stock.salePrice"
        );
        String currency = firstText(product, "currency", "currencyCode", "product.currency");
        products.add(UberEatsDtos.ProductResponse.builder()
                .id(id)
                .name(name)
                .description(description)
                .categoryName(categoryName)
                .price(price)
                .currency(currency == null ? "TRY" : currency)
                .imageUrl(firstText(
                        product,
                        "imageUrl",
                        "image",
                        "photoUrl",
                        "images.0",
                        "images.0.url",
                        "product.images.0",
                        "product.imageUrl"
                ))
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
