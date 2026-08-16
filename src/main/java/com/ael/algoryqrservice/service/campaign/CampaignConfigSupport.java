package com.ael.algoryqrservice.service.campaign;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CampaignConfigSupport {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final ObjectMapper objectMapper;

    public JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    public String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node != null ? node : objectMapper.createObjectNode());
        } catch (Exception ex) {
            return "{}";
        }
    }

    public Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public String writeJsonMap(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : Collections.emptyMap());
        } catch (Exception ex) {
            return "{}";
        }
    }

    public String writeJsonObject(ObjectNode node) {
        return writeJson(node);
    }

    public ObjectNode emptyObject() {
        return objectMapper.createObjectNode();
    }

    public List<Long> targetProductIds(JsonNode config) {
        Set<Long> ids = new HashSet<>();
        JsonNode node = config.path("targetProductIds");
        if (node.isArray()) {
            node.forEach(item -> {
                if (item.isNumber()) {
                    ids.add(item.longValue());
                }
            });
        }
        return new ArrayList<>(ids);
    }

    public int requiredQuantity(JsonNode config) {
        return Math.max(1, config.path("requiredQuantity").asInt(1));
    }

    public boolean resetAfterReward(JsonNode config) {
        return !config.has("resetAfterReward") || config.path("resetAfterReward").asBoolean(true);
    }

    public BigDecimal thresholdAmount(JsonNode config) {
        if (config.hasNonNull("thresholdAmount")) {
            return new BigDecimal(config.path("thresholdAmount").asText("0"));
        }
        return BigDecimal.ZERO;
    }

    public String period(JsonNode config) {
        String value = config.path("period").asText("WEEKLY");
        return "MONTHLY".equalsIgnoreCase(value) ? "MONTHLY" : "WEEKLY";
    }

    public String periodKey(LocalDateTime at, String period) {
        LocalDate date = at.atZone(ZONE).toLocalDate();
        if ("MONTHLY".equals(period)) {
            return date.getYear() + "-M" + String.format("%02d", date.getMonthValue());
        }
        WeekFields weekFields = WeekFields.of(Locale.forLanguageTag("tr-TR"));
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int year = date.get(weekFields.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }

    public JsonNode rewardNode(JsonNode config) {
        return config.path("reward");
    }

    public String rewardType(JsonNode reward) {
        return reward.path("type").asText("FREE_PRODUCT");
    }

    public int currentStamps(JsonNode state) {
        return Math.max(0, state.path("stamps").asInt(0));
    }

    public BigDecimal currentSpend(JsonNode state, String periodKey) {
        JsonNode spendByPeriod = state.path("spendByPeriod");
        if (spendByPeriod.has(periodKey)) {
            return new BigDecimal(spendByPeriod.path(periodKey).asText("0"));
        }
        return BigDecimal.ZERO;
    }

    public ObjectNode setStamps(ObjectNode state, int stamps) {
        state.put("stamps", Math.max(0, stamps));
        return state;
    }

    public ObjectNode addSpend(ObjectNode state, String periodKey, BigDecimal amount) {
        JsonNode spendByPeriod = state.path("spendByPeriod");
        ObjectNode spendNode = spendByPeriod.isObject()
                ? (ObjectNode) spendByPeriod.deepCopy()
                : objectMapper.createObjectNode();
        BigDecimal current = currentSpend(state, periodKey);
        spendNode.put(periodKey, current.add(amount).toPlainString());
        state.set("spendByPeriod", spendNode);
        return state;
    }

    public ObjectNode resetSpendPeriod(ObjectNode state, String periodKey) {
        ObjectNode spendNode = state.path("spendByPeriod").isObject()
                ? (ObjectNode) state.path("spendByPeriod").deepCopy()
                : objectMapper.createObjectNode();
        spendNode.put(periodKey, "0");
        state.set("spendByPeriod", spendNode);
        return state;
    }
}
