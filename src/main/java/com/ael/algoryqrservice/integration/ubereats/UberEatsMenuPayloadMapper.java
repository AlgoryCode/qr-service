package com.ael.algoryqrservice.integration.ubereats;

import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class UberEatsMenuPayloadMapper {

    private final ObjectMapper objectMapper;

    public JsonNode toUberItem(IntegrationPendingProduct product) {
        JsonNode data = product.getProductData();
        ObjectNode item = objectMapper.createObjectNode();
        String itemId = product.getUberItemId() != null && !product.getUberItemId().isBlank()
                ? product.getUberItemId()
                : ("item-" + (product.getSourceProductId() == null ? product.getId() : product.getSourceProductId()));
        item.put("id", itemId);
        item.set("title", multiLang(text(data, "name")));
        item.set("description", multiLang(text(data, "description")));
        ObjectNode priceInfo = item.putObject("price_info");
        priceInfo.put("price", toMinorUnits(data));
        String imageUrl = text(data, "imageUrl");
        if (imageUrl != null && !imageUrl.isBlank()) {
            ArrayNode imageUrlArray = item.putArray("image_url");
            imageUrlArray.add(imageUrl);
        }
        boolean available = data == null || !data.has("available") || data.get("available").asBoolean(true);
        if (!available) {
            ObjectNode suspension = item.putObject("suspension_info").putObject("suspension");
            suspension.put("suspend_until", 8640000000L);
            suspension.putNull("reason");
        }
        item.put("external_data", product.getSourceProductId() == null ? "" : product.getSourceProductId());
        return item;
    }

    public JsonNode toUpdateItemPayload(IntegrationPendingProduct product) {
        ObjectNode payload = objectMapper.createObjectNode();
        JsonNode item = toUberItem(product);
        payload.set("price_info", item.get("price_info"));
        payload.set("title", item.get("title"));
        payload.set("description", item.get("description"));
        if (item.has("image_url")) {
            payload.set("image_url", item.get("image_url"));
        }
        if (item.has("suspension_info")) {
            payload.set("suspension_info", item.get("suspension_info"));
        } else {
            ObjectNode suspensionInfo = payload.putObject("suspension_info");
            suspensionInfo.putNull("suspension");
        }
        return payload;
    }

    public ObjectNode upsertItemIntoMenu(JsonNode existingMenu, JsonNode item, String categoryName) {
        ObjectNode menu = existingMenu == null || !existingMenu.isObject()
                ? emptyMenu()
                : (ObjectNode) existingMenu.deepCopy();
        ensureArrays(menu);
        String categoryId = ensureCategory(menu, categoryName);
        ensureMenuLinksCategory(menu, categoryId);
        upsertEntity(menu.withArray("items"), item);
        ObjectNode category = findById(menu.withArray("categories"), categoryId);
        if (category != null) {
            ArrayNode entities = category.withArray("entities");
            boolean linked = false;
            String itemId = item.path("id").asText();
            for (JsonNode entity : entities) {
                if (itemId.equals(entity.path("id").asText())) {
                    linked = true;
                    break;
                }
            }
            if (!linked) {
                ObjectNode entity = entities.addObject();
                entity.put("type", "ITEM");
                entity.put("id", itemId);
            }
        }
        return menu;
    }

    public ObjectNode emptyMenu() {
        ObjectNode menu = objectMapper.createObjectNode();
        ArrayNode menus = menu.putArray("menus");
        ObjectNode defaultMenu = menus.addObject();
        defaultMenu.put("id", "menu-main");
        defaultMenu.set("title", multiLang("Main Menu"));
        defaultMenu.putArray("category_ids");
        ObjectNode availability = defaultMenu.putArray("service_availability").addObject();
        availability.put("day_of_week", "monday");
        ArrayNode periods = availability.putArray("time_periods");
        ObjectNode period = periods.addObject();
        period.put("start_time", "00:00");
        period.put("end_time", "23:59");
        menu.putArray("categories");
        menu.putArray("items");
        menu.putArray("modifier_groups");
        return menu;
    }

    private void ensureArrays(ObjectNode menu) {
        if (!menu.has("menus") || !menu.get("menus").isArray()) {
            menu.putArray("menus");
        }
        if (!menu.has("categories") || !menu.get("categories").isArray()) {
            menu.putArray("categories");
        }
        if (!menu.has("items") || !menu.get("items").isArray()) {
            menu.putArray("items");
        }
        if (!menu.has("modifier_groups") || !menu.get("modifier_groups").isArray()) {
            menu.putArray("modifier_groups");
        }
        if (menu.withArray("menus").isEmpty()) {
            ObjectNode defaultMenu = menu.withArray("menus").addObject();
            defaultMenu.put("id", "menu-main");
            defaultMenu.set("title", multiLang("Main Menu"));
            defaultMenu.putArray("category_ids");
        }
    }

    private String ensureCategory(ObjectNode menu, String categoryName) {
        String name = categoryName == null || categoryName.isBlank() ? "General" : categoryName.trim();
        ArrayNode categories = menu.withArray("categories");
        for (JsonNode category : categories) {
            String title = firstText(category.path("title"));
            if (name.equalsIgnoreCase(title)) {
                return category.path("id").asText();
            }
        }
        String categoryId = "cat-" + slug(name);
        ObjectNode category = categories.addObject();
        category.put("id", categoryId);
        category.set("title", multiLang(name));
        category.putArray("entities");
        return categoryId;
    }

    private void ensureMenuLinksCategory(ObjectNode menu, String categoryId) {
        ArrayNode menus = menu.withArray("menus");
        ObjectNode firstMenu = (ObjectNode) menus.get(0);
        ArrayNode categoryIds = firstMenu.withArray("category_ids");
        for (JsonNode id : categoryIds) {
            if (categoryId.equals(id.asText())) {
                return;
            }
        }
        categoryIds.add(categoryId);
    }

    private void upsertEntity(ArrayNode items, JsonNode item) {
        String itemId = item.path("id").asText();
        for (int i = 0; i < items.size(); i++) {
            if (itemId.equals(items.get(i).path("id").asText())) {
                items.set(i, item);
                return;
            }
        }
        items.add(item);
    }

    private ObjectNode findById(ArrayNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (id.equals(node.path("id").asText()) && node.isObject()) {
                return (ObjectNode) node;
            }
        }
        return null;
    }

    private ObjectNode multiLang(String value) {
        ObjectNode node = objectMapper.createObjectNode();
        ObjectNode translations = node.putObject("translations");
        translations.put("en_us", value == null ? "" : value);
        return node;
    }

    private int toMinorUnits(JsonNode data) {
        if (data == null || !data.hasNonNull("price")) {
            return 0;
        }
        BigDecimal price = data.get("price").decimalValue();
        return price.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private String text(JsonNode data, String field) {
        if (data == null || !data.hasNonNull(field)) {
            return null;
        }
        return data.get(field).asText();
    }

    private String firstText(JsonNode multiLang) {
        if (multiLang == null || multiLang.isMissingNode() || multiLang.isNull()) {
            return null;
        }
        if (multiLang.isTextual()) {
            return multiLang.asText();
        }
        JsonNode translations = multiLang.get("translations");
        if (translations != null && translations.isObject()) {
            if (translations.hasNonNull("en_us")) {
                return translations.get("en_us").asText();
            }
            var fields = translations.fields();
            if (fields.hasNext()) {
                return fields.next().getValue().asText(null);
            }
        }
        if (translations != null && translations.isArray() && !translations.isEmpty()) {
            return translations.get(0).path("value").asText(null);
        }
        return multiLang.path("en_us").asText(null);
    }

    private String slug(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
