package com.ael.algoryqrservice.integration.ubereats;

import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClientException;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.ubereats.service.UberEatsConnectionService;
import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@RequiredArgsConstructor
public class HttpUberEatsMenuPublisher implements UberEatsMenuPublisher {

    private final UberEatsClient uberEatsClient;
    private final UberEatsConnectionService connectionService;
    private final UberEatsMenuPayloadMapper payloadMapper;

    @Override
    public PublishResult publishItem(IntegrationPendingProduct product, JsonNode ignoredLegacyPayload) {
        UberEatsConnection connection = connectionService.requireConnected(product.getMenuId());
        UberEatsDtos.Credentials credentials = connectionService.decrypt(connection);
        JsonNode item = payloadMapper.toUberItem(product);
        String itemId = item.path("id").asText();
        try {
            uberEatsClient.updateItem(credentials, itemId, payloadMapper.toUpdateItemPayload(product));
            return PublishResult.ok(itemId);
        } catch (UberEatsClientException updateError) {
            if (updateError.getStatusCode() != 404) {
                return PublishResult.failed(updateError.getMessage(), updateError.isRetryable());
            }
            try {
                JsonNode currentMenu = uberEatsClient.getMenu(credentials);
                String category = product.getProductData() == null
                        ? null
                        : text(product.getProductData(), "category");
                if (category == null || category.isBlank()) {
                    category = text(product.getProductData(), "subcategory");
                }
                ObjectNode merged = payloadMapper.upsertItemIntoMenu(currentMenu, item, category);
                uberEatsClient.uploadMenu(credentials, merged);
                return PublishResult.ok(itemId);
            } catch (UberEatsClientException uploadError) {
                return PublishResult.failed(uploadError.getMessage(), uploadError.isRetryable());
            }
        }
    }

    private String text(JsonNode data, String field) {
        if (data == null || !data.hasNonNull(field)) {
            return null;
        }
        return data.get(field).asText();
    }
}
