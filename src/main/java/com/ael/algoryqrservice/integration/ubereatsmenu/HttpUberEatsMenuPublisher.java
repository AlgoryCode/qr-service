package com.ael.algoryqrservice.integration.ubereatsmenu;

import com.ael.algoryqrservice.integration.ubereatsmenu.client.UberEatsMenuAuthClient;
import com.ael.algoryqrservice.integration.ubereatsmenu.client.UberEatsMenuClientException;
import com.ael.algoryqrservice.integration.ubereatsmenu.model.UberEatsMenuConnection;
import com.ael.algoryqrservice.integration.ubereatsmenu.model.dto.UberEatsMenuDtos;
import com.ael.algoryqrservice.integration.ubereatsmenu.service.UberEatsMenuConnectionService;
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

    private final UberEatsMenuAuthClient uberEatsMenuAuthClient;
    private final UberEatsMenuConnectionService connectionService;
    private final UberEatsMenuPayloadMapper payloadMapper;

    @Override
    public PublishResult publishItem(IntegrationPendingProduct product, JsonNode ignoredLegacyPayload) {
        UberEatsMenuConnection connection = connectionService.requireConnected(product.getMenuId());
        UberEatsMenuDtos.Credentials credentials = connectionService.decrypt(connection);
        JsonNode item = payloadMapper.toUberItem(product);
        String itemId = item.path("id").asText();
        try {
            uberEatsMenuAuthClient.updateItem(credentials, itemId, payloadMapper.toUpdateItemPayload(product));
            return PublishResult.ok(itemId);
        } catch (UberEatsMenuClientException updateError) {
            if (updateError.getStatusCode() != 404) {
                return PublishResult.failed(updateError.getMessage(), updateError.isRetryable());
            }
            try {
                JsonNode currentMenu = uberEatsMenuAuthClient.getMenu(credentials);
                String category = product.getProductData() == null
                        ? null
                        : text(product.getProductData(), "category");
                if (category == null || category.isBlank()) {
                    category = text(product.getProductData(), "subcategory");
                }
                ObjectNode merged = payloadMapper.upsertItemIntoMenu(currentMenu, item, category);
                uberEatsMenuAuthClient.uploadMenu(credentials, merged);
                return PublishResult.ok(itemId);
            } catch (UberEatsMenuClientException uploadError) {
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
